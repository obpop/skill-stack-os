package com.skillstack.operations;

import com.skillstack.common.AuditedEntity;
import com.skillstack.intelligence.RecommendationResult;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "background_job")
public class BackgroundJob extends AuditedEntity {
    public enum OperationType { RECOMMENDATION }
    public enum Status { QUEUED, RUNNING, RETRY_SCHEDULED, SUCCEEDED, FAILED, CANCELLED }
    public enum Stage { READING_STACK, COMPARING_COMBINATIONS, CHECKING_CATALOG, PREPARING_RECOMMENDATIONS, COMPLETED }

    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 40) private OperationType operationType;
    @Column(name = "request_key", nullable = false, length = 180) private String requestKey;
    @Column(name = "input_hash", nullable = false, length = 64) private String inputHash;
    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String requestPayload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Enumerated(EnumType.STRING) @Column(name = "progress_stage", nullable = false, length = 40) private Stage progressStage;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(nullable = false) private short priority;
    @Column(name = "available_at", nullable = false) private OffsetDateTime availableAt;
    @Column(name = "lease_owner", length = 120) private String leaseOwner;
    @Column(name = "lease_until") private OffsetDateTime leaseUntil;
    @Column(name = "heartbeat_at") private OffsetDateTime heartbeatAt;
    @Column(name = "cancellation_requested", nullable = false) private boolean cancellationRequested;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_reference_id") private RecommendationResult resultReference;
    @Column(name = "safe_error_code", length = 80) private String safeErrorCode;
    @Column(name = "completed_at") private OffsetDateTime completedAt;

    protected BackgroundJob() {}

    public BackgroundJob(AppUser owner, String requestKey, String inputHash, String requestPayload, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.operationType = OperationType.RECOMMENDATION;
        this.requestKey = requestKey;
        this.inputHash = inputHash;
        this.requestPayload = requestPayload;
        this.status = Status.QUEUED;
        this.progressStage = Stage.READING_STACK;
        this.maxAttempts = maxAttempts;
        this.availableAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void claim(String workerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
        if (status != Status.QUEUED && status != Status.RETRY_SCHEDULED) throw new IllegalStateException("Job is not claimable");
        status = Status.RUNNING;
        attemptCount++;
        leaseOwner = workerId;
        this.leaseUntil = leaseUntil;
        heartbeatAt = now;
        safeErrorCode = null;
    }

    public void stage(String workerId, Stage stage, OffsetDateTime now, OffsetDateTime leaseUntil) {
        requireLease(workerId);
        progressStage = stage;
        heartbeatAt = now;
        this.leaseUntil = leaseUntil;
    }

    public void succeed(String workerId, RecommendationResult result) {
        requireLease(workerId);
        status = Status.SUCCEEDED;
        progressStage = Stage.COMPLETED;
        resultReference = result;
        completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        clearLease();
    }

    public void fail(String workerId, String safeErrorCode) {
        requireLease(workerId);
        status = Status.FAILED;
        this.safeErrorCode = safeErrorCode;
        completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        clearLease();
    }

    public void retry(String workerId, String safeErrorCode, OffsetDateTime availableAt) {
        requireLease(workerId);
        if (attemptCount >= maxAttempts) { fail(workerId, safeErrorCode); return; }
        status = Status.RETRY_SCHEDULED;
        this.safeErrorCode = safeErrorCode;
        this.availableAt = availableAt;
        clearLease();
    }

    public void requestCancellation() {
        cancellationRequested = true;
        if (status == Status.QUEUED || status == Status.RETRY_SCHEDULED) {
            status = Status.CANCELLED;
            completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            safeErrorCode = "CANCELLED_BY_USER";
        }
    }

    public void cancelRunning(String workerId) {
        requireLease(workerId);
        status = Status.CANCELLED;
        completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        safeErrorCode = "CANCELLED_BY_USER";
        clearLease();
    }

    public void requeueAfterLeaseExpiry(OffsetDateTime now) {
        if (status != Status.RUNNING || leaseUntil == null || !leaseUntil.isBefore(now)) return;
        if (attemptCount >= maxAttempts) {
            status = Status.FAILED;
            completedAt = now;
            safeErrorCode = "LEASE_EXPIRED";
        } else {
            status = Status.RETRY_SCHEDULED;
            availableAt = now;
            safeErrorCode = "LEASE_EXPIRED";
        }
        clearLease();
    }

    public void manualRetry(String currentInputHash) {
        if (status != Status.FAILED) throw new IllegalStateException("Only failed jobs can be retried");
        if (!inputHash.equals(currentInputHash)) throw new IllegalStateException("Job input is stale");
        status = Status.QUEUED;
        availableAt = OffsetDateTime.now(ZoneOffset.UTC);
        completedAt = null;
        safeErrorCode = null;
        cancellationRequested = false;
        attemptCount = 0;
        progressStage = Stage.READING_STACK;
    }

    private void requireLease(String workerId) {
        if (status != Status.RUNNING || leaseOwner == null || !leaseOwner.equals(workerId)) throw new IllegalStateException("Stale job lease");
    }

    private void clearLease() { leaseOwner = null; leaseUntil = null; heartbeatAt = null; }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public OperationType getOperationType() { return operationType; }
    public String getRequestKey() { return requestKey; }
    public String getInputHash() { return inputHash; }
    public String getRequestPayload() { return requestPayload; }
    public Status getStatus() { return status; }
    public Stage getProgressStage() { return progressStage; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public OffsetDateTime getAvailableAt() { return availableAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public OffsetDateTime getLeaseUntil() { return leaseUntil; }
    public OffsetDateTime getHeartbeatAt() { return heartbeatAt; }
    public boolean isCancellationRequested() { return cancellationRequested; }
    public RecommendationResult getResultReference() { return resultReference; }
    public String getSafeErrorCode() { return safeErrorCode; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}
