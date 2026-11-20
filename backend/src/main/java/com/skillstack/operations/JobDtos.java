package com.skillstack.operations;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class JobDtos {
    private JobDtos() {}

    public record StartRecommendationRequest(
            @Size(max = 3) List<UUID> skillIds,
            boolean refresh,
            @Size(max = 180) @Pattern(regexp = "[A-Za-z0-9._:-]+") String requestKey
    ) {}

    public record Job(
            UUID id, BackgroundJob.OperationType operationType, BackgroundJob.Status status,
            BackgroundJob.Stage stage, int attemptCount, int maxAttempts,
            UUID resultId, String safeErrorCode, boolean cancellationRequested,
            OffsetDateTime availableAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            OffsetDateTime completedAt
    ) {}

    public record Summary(long queued, long running, long retryScheduled, long failed, long cancelled,
                          long succeeded, Long oldestPendingAgeSeconds) {}
}
