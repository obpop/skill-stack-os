package com.skillstack.operations;

import com.skillstack.common.ApiException;
import com.skillstack.intelligence.IntelligenceDtos;
import com.skillstack.intelligence.RecommendationResultRepository;
import com.skillstack.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class BackgroundJobService {
    private static final List<BackgroundJob.Status> ACTIVE = List.of(
            BackgroundJob.Status.QUEUED, BackgroundJob.Status.RUNNING, BackgroundJob.Status.RETRY_SCHEDULED);
    private static final List<BackgroundJob.Status> TERMINAL = List.of(
            BackgroundJob.Status.SUCCEEDED, BackgroundJob.Status.FAILED, BackgroundJob.Status.CANCELLED);

    private final BackgroundJobRepository jobs;
    private final RecommendationResultRepository results;
    private final UserService users;
    private final JobInputSnapshotService snapshots;
    private final JobPolicy policy;
    private final ObjectMapper objectMapper;

    public BackgroundJobService(BackgroundJobRepository jobs, RecommendationResultRepository results,
                                UserService users, JobInputSnapshotService snapshots,
                                JobPolicy policy, ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.results = results;
        this.users = users;
        this.snapshots = snapshots;
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobDtos.Job startRecommendation(String subject, JobDtos.StartRecommendationRequest request) {
        var skillIds = request.skillIds() == null ? List.<UUID>of() : request.skillIds().stream().distinct().toList();
        if (request.skillIds() != null && skillIds.size() != request.skillIds().size()) {
            throw ApiException.badRequest("DUPLICATE_SKILL", "Choose distinct skills");
        }
        var inputHash = snapshots.snapshot(subject, skillIds);
        var requestKey = cleanRequestKey(request.requestKey());
        if (request.refresh() && requestKey == null) {
            throw ApiException.badRequest("REQUEST_KEY_REQUIRED", "A refresh requires an idempotency key");
        }
        if (requestKey == null) requestKey = "current:" + inputHash;

        jobs.lockOwnerSubmission(subject);
        var existing = jobs.findByOwnerSubjectAndOperationTypeAndRequestKey(subject, BackgroundJob.OperationType.RECOMMENDATION, requestKey);
        if (existing.isPresent()) {
            if (!existing.get().getInputHash().equals(inputHash)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Use a new request key for changed analysis input");
            }
            return response(existing.get());
        }
        if (jobs.countByOwnerSubjectAndStatusIn(subject, ACTIVE) >= policy.maxActivePerUser()) {
            throw ApiException.conflict("JOB_LIMIT_REACHED", "Finish or cancel an active analysis before starting another");
        }
        if (jobs.countByStatusIn(ACTIVE) >= policy.maxQueuedTotal()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "JOB_QUEUE_FULL", "Analysis is temporarily busy. Try again shortly");
        }
        var payload = write(new RecommendationPayload(skillIds, request.refresh()));
        var job = new BackgroundJob(users.require(subject), requestKey, inputHash, payload, 2);
        return response(jobs.saveAndFlush(job));
    }

    @Transactional(readOnly = true)
    public JobDtos.Job get(String subject, UUID id) { return response(require(subject, id)); }

    @Transactional(readOnly = true)
    public List<JobDtos.Job> recent(String subject) {
        return jobs.findTop20ByOwnerSubjectOrderByCreatedAtDesc(subject).stream().map(this::response).toList();
    }

    @Transactional
    public JobDtos.Job cancel(String subject, UUID id) {
        var job = require(subject, id);
        if (job.getStatus() == BackgroundJob.Status.SUCCEEDED || job.getStatus() == BackgroundJob.Status.FAILED
                || job.getStatus() == BackgroundJob.Status.CANCELLED) return response(job);
        job.requestCancellation();
        return response(jobs.saveAndFlush(job));
    }

    @Transactional
    public JobDtos.Job retry(String subject, UUID id) {
        var job = require(subject, id);
        var payload = read(job.getRequestPayload());
        var currentHash = snapshots.snapshot(subject, payload.skillIds());
        try { job.manualRetry(currentHash); }
        catch (IllegalStateException problem) {
            if (!job.getInputHash().equals(currentHash)) throw ApiException.conflict("JOB_INPUT_CHANGED", "Your stack changed. Start a new analysis instead");
            throw ApiException.conflict("JOB_NOT_RETRYABLE", "Only a failed analysis can be retried");
        }
        return response(jobs.saveAndFlush(job));
    }

    @Transactional
    public BackgroundJob claim(String workerId) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var job = jobs.findNextClaimable().orElse(null);
        if (job == null) return null;
        job.claim(workerId, now, now.plus(policy.leaseDuration()));
        var claimed = jobs.saveAndFlush(job);
        claimed.getOwner().getSubject();
        return claimed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStage(UUID id, String workerId, BackgroundJob.Stage stage) {
        var job = jobs.findById(id).orElseThrow();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        job.stage(workerId, stage, now, now.plus(policy.leaseDuration()));
        jobs.saveAndFlush(job);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean canPublish(UUID id, String workerId) {
        var job = jobs.findById(id).orElse(null);
        if (job == null || job.getStatus() != BackgroundJob.Status.RUNNING || job.isCancellationRequested()
                || !workerId.equals(job.getLeaseOwner())) return false;
        var payload = read(job.getRequestPayload());
        return job.getInputHash().equals(snapshots.snapshot(job.getOwner().getSubject(), payload.skillIds()));
    }

    @Transactional
    public void succeed(UUID id, String workerId, UUID resultId) {
        var job = jobs.findById(id).orElseThrow();
        if (!canPublish(id, workerId)) throw new GenerationSuppressedException();
        var result = results.findByIdAndOwnerSubject(resultId, job.getOwner().getSubject()).orElseThrow();
        job.succeed(workerId, result);
        jobs.saveAndFlush(job);
    }

    @Transactional
    public void suppress(UUID id, String workerId) {
        var job = jobs.findById(id).orElse(null);
        if (job == null || job.getStatus() != BackgroundJob.Status.RUNNING || !workerId.equals(job.getLeaseOwner())) return;
        if (job.isCancellationRequested()) job.cancelRunning(workerId);
        else job.fail(workerId, "INPUT_CHANGED");
        jobs.saveAndFlush(job);
    }

    @Transactional
    public void failOrRetry(UUID id, String workerId, String code, boolean transientFailure) {
        var job = jobs.findById(id).orElse(null);
        if (job == null || job.getStatus() != BackgroundJob.Status.RUNNING || !workerId.equals(job.getLeaseOwner())) return;
        if (job.isCancellationRequested()) job.cancelRunning(workerId);
        else if (transientFailure) {
            var seconds = Math.min(30, (1L << Math.min(job.getAttemptCount(), 4)) + Math.floorMod(id.hashCode(), 3));
            job.retry(workerId, code, OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(seconds));
        } else job.fail(workerId, code);
        jobs.saveAndFlush(job);
    }

    @Transactional
    public int recoverExpiredLeases() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var expired = jobs.findByStatusAndLeaseUntilBefore(BackgroundJob.Status.RUNNING, now);
        expired.forEach(job -> job.requeueAfterLeaseExpiry(now));
        jobs.saveAll(expired);
        return expired.size();
    }

    @Transactional
    public long cleanup() {
        return jobs.deleteByStatusInAndCompletedAtBefore(TERMINAL,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(policy.retentionDays()));
    }

    @Transactional(readOnly = true)
    public JobDtos.Summary summary(String subject) {
        return new JobDtos.Summary(jobs.countByOwnerSubjectAndStatus(subject, BackgroundJob.Status.QUEUED),
                jobs.countByOwnerSubjectAndStatus(subject, BackgroundJob.Status.RUNNING),
                jobs.countByOwnerSubjectAndStatus(subject, BackgroundJob.Status.RETRY_SCHEDULED),
                jobs.countByOwnerSubjectAndStatus(subject, BackgroundJob.Status.FAILED),
                jobs.countByOwnerSubjectAndStatus(subject, BackgroundJob.Status.CANCELLED),
                jobs.countByOwnerSubjectAndStatus(subject, BackgroundJob.Status.SUCCEEDED), null);
    }

    @Transactional(readOnly = true)
    public JobDtos.Summary systemSummary() {
        var oldest = jobs.oldestPendingCreatedAt().map(value -> Math.max(0,
                java.time.Duration.between(value, OffsetDateTime.now(ZoneOffset.UTC)).toSeconds())).orElse(null);
        return new JobDtos.Summary(jobs.countByStatus(BackgroundJob.Status.QUEUED),
                jobs.countByStatus(BackgroundJob.Status.RUNNING), jobs.countByStatus(BackgroundJob.Status.RETRY_SCHEDULED),
                jobs.countByStatus(BackgroundJob.Status.FAILED), jobs.countByStatus(BackgroundJob.Status.CANCELLED),
                jobs.countByStatus(BackgroundJob.Status.SUCCEEDED), oldest);
    }

    public RecommendationPayload readRequest(BackgroundJob job) { return read(job.getRequestPayload()); }

    private BackgroundJob require(String subject, UUID id) {
        return jobs.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("job"));
    }

    private JobDtos.Job response(BackgroundJob job) {
        return new JobDtos.Job(job.getId(), job.getOperationType(), job.getStatus(), job.getProgressStage(),
                job.getAttemptCount(), job.getMaxAttempts(), job.getResultReference() == null ? null : job.getResultReference().getId(),
                job.getSafeErrorCode(), job.isCancellationRequested(), job.getAvailableAt(), job.getCreatedAt(),
                job.getUpdatedAt(), job.getCompletedAt());
    }

    private String cleanRequestKey(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception problem) { throw new IllegalStateException("Unable to serialize job request", problem); }
    }

    private RecommendationPayload read(String value) {
        try { return objectMapper.readValue(value, RecommendationPayload.class); }
        catch (Exception problem) { throw new IllegalStateException("Stored job request is invalid", problem); }
    }

    public record RecommendationPayload(List<UUID> skillIds, boolean refresh) {}
    public static final class GenerationSuppressedException extends RuntimeException {}
}
