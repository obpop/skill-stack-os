package com.skillstack.operations;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("backgroundJobs")
public class BackgroundJobHealthIndicator implements HealthIndicator {
    private final BackgroundJobService jobs;
    private final JobPolicy policy;

    public BackgroundJobHealthIndicator(BackgroundJobService jobs, JobPolicy policy) {
        this.jobs = jobs;
        this.policy = policy;
    }

    @Override
    public Health health() {
        var summary = jobs.systemSummary();
        var stale = summary.oldestPendingAgeSeconds() != null
                && summary.oldestPendingAgeSeconds() > policy.healthMaxPendingAgeSeconds();
        var builder = stale ? Health.outOfService() : Health.up();
        return builder
                .withDetail("queued", summary.queued())
                .withDetail("running", summary.running())
                .withDetail("retryScheduled", summary.retryScheduled())
                .withDetail("failed", summary.failed())
                .withDetail("oldestPendingAgeSeconds", summary.oldestPendingAgeSeconds() == null ? 0 : summary.oldestPendingAgeSeconds())
                .withDetail("maxPendingAgeSeconds", policy.healthMaxPendingAgeSeconds())
                .build();
    }
}
