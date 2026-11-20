package com.skillstack.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class JobPolicy {
    private final boolean enabled;
    private final long pollMs;
    private final int leaseSeconds;
    private final int maxActivePerUser;
    private final int maxQueuedTotal;
    private final int retentionDays;
    private final int healthMaxPendingAgeSeconds;

    public JobPolicy(
            @Value("${skillstack.jobs.enabled:true}") boolean enabled,
            @Value("${skillstack.jobs.poll-ms:750}") long pollMs,
            @Value("${skillstack.jobs.lease-seconds:45}") int leaseSeconds,
            @Value("${skillstack.jobs.max-active-per-user:2}") int maxActivePerUser,
            @Value("${skillstack.jobs.max-queued-total:200}") int maxQueuedTotal,
            @Value("${skillstack.jobs.retention-days:30}") int retentionDays,
            @Value("${skillstack.jobs.health-max-pending-age-seconds:120}") int healthMaxPendingAgeSeconds) {
        this.enabled = enabled;
        this.pollMs = Math.max(250, Math.min(10_000, pollMs));
        this.leaseSeconds = Math.max(15, Math.min(300, leaseSeconds));
        this.maxActivePerUser = Math.max(1, Math.min(10, maxActivePerUser));
        this.maxQueuedTotal = Math.max(10, Math.min(10_000, maxQueuedTotal));
        this.retentionDays = Math.max(1, Math.min(365, retentionDays));
        this.healthMaxPendingAgeSeconds = Math.max(30, Math.min(3600, healthMaxPendingAgeSeconds));
    }

    public boolean enabled() { return enabled; }
    public long pollMs() { return pollMs; }
    public Duration leaseDuration() { return Duration.ofSeconds(leaseSeconds); }
    public int maxActivePerUser() { return maxActivePerUser; }
    public int maxQueuedTotal() { return maxQueuedTotal; }
    public int retentionDays() { return retentionDays; }
    public int healthMaxPendingAgeSeconds() { return healthMaxPendingAgeSeconds; }
}
