package com.skillstack.operations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {
    Optional<BackgroundJob> findByIdAndOwnerSubject(UUID id, String subject);
    Optional<BackgroundJob> findByOwnerSubjectAndOperationTypeAndRequestKey(String subject, BackgroundJob.OperationType operationType, String requestKey);
    List<BackgroundJob> findTop20ByOwnerSubjectOrderByCreatedAtDesc(String subject);
    long countByOwnerSubjectAndStatusIn(String subject, List<BackgroundJob.Status> statuses);
    long countByStatusIn(List<BackgroundJob.Status> statuses);
    long countByStatus(BackgroundJob.Status status);
    long countByOwnerSubjectAndStatus(String subject, BackgroundJob.Status status);
    long deleteByStatusInAndCompletedAtBefore(List<BackgroundJob.Status> statuses, OffsetDateTime before);

    @Query(value = """
            SELECT * FROM background_job
            WHERE status IN ('QUEUED', 'RETRY_SCHEDULED')
              AND available_at <= CURRENT_TIMESTAMP
              AND cancellation_requested = FALSE
            ORDER BY priority DESC, created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<BackgroundJob> findNextClaimable();

    List<BackgroundJob> findByStatusAndLeaseUntilBefore(BackgroundJob.Status status, OffsetDateTime now);

    @Query("select min(j.createdAt) from BackgroundJob j where j.status in ('QUEUED', 'RETRY_SCHEDULED')")
    Optional<OffsetDateTime> oldestPendingCreatedAt();

    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:subject))", nativeQuery = true)
    Integer lockOwnerSubmission(@Param("subject") String subject);
}
