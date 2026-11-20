package com.skillstack.operations;

import com.skillstack.common.ApiException;
import com.skillstack.intelligence.IntelligenceDtos;
import com.skillstack.intelligence.IntelligenceService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "skillstack.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobWorker {
    private static final Logger log = LoggerFactory.getLogger(BackgroundJobWorker.class);
    private final String workerId = "local-" + UUID.randomUUID();
    private final BackgroundJobService jobs;
    private final IntelligenceService intelligence;
    private final MeterRegistry meters;

    public BackgroundJobWorker(BackgroundJobService jobs, IntelligenceService intelligence, MeterRegistry meters) {
        this.jobs = jobs;
        this.intelligence = intelligence;
        this.meters = meters;
        Gauge.builder("skillstack.jobs.pending", jobs, service -> {
            var summary = service.systemSummary();
            return summary.queued() + summary.retryScheduled();
        }).register(meters);
        Gauge.builder("skillstack.jobs.running", jobs, service -> service.systemSummary().running()).register(meters);
        Gauge.builder("skillstack.jobs.oldest.age.seconds", jobs,
                service -> service.systemSummary().oldestPendingAgeSeconds() == null ? 0 : service.systemSummary().oldestPendingAgeSeconds())
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${skillstack.jobs.poll-ms:750}", initialDelayString = "${skillstack.jobs.poll-ms:750}")
    public void poll() {
        var recovered = jobs.recoverExpiredLeases();
        if (recovered > 0) {
            meters.counter("skillstack.jobs.recovered").increment(recovered);
            log.warn("Recovered expired background jobs count={}", recovered);
        }
        var job = jobs.claim(workerId);
        if (job == null) return;
        execute(job);
    }

    void execute(BackgroundJob job) {
        var id = job.getId();
        try {
            var request = jobs.readRequest(job);
            if (!jobs.canPublish(id, workerId)) throw new IntelligenceService.GenerationSuppressedException();
            var recommendation = intelligence.generateForJob(job.getOwner().getSubject(),
                    new IntelligenceDtos.GenerateRequest(request.skillIds(), request.refresh()),
                    () -> jobs.canPublish(id, workerId),
                    stage -> jobs.updateStage(id, workerId, BackgroundJob.Stage.valueOf(stage.name())));
            jobs.succeed(id, workerId, recommendation.recommendationId());
            meters.counter("skillstack.jobs.completed", "operation", "recommendation").increment();
            log.info("Background job completed jobId={} operation={} attempts={}", id, job.getOperationType(), job.getAttemptCount());
        } catch (IntelligenceService.GenerationSuppressedException | BackgroundJobService.GenerationSuppressedException suppressed) {
            jobs.suppress(id, workerId);
            meters.counter("skillstack.jobs.suppressed").increment();
            log.info("Background job publication suppressed jobId={}", id);
        } catch (TransientDataAccessException transientFailure) {
            jobs.failOrRetry(id, workerId, "DATABASE_TRANSIENT", true);
            meters.counter("skillstack.jobs.retry", "reason", "database_transient").increment();
            log.warn("Background job scheduled for bounded retry jobId={} code=DATABASE_TRANSIENT", id);
        } catch (ApiException permanent) {
            jobs.failOrRetry(id, workerId, permanent.code(), false);
            meters.counter("skillstack.jobs.failed", "code", permanent.code()).increment();
            log.warn("Background job failed jobId={} code={}", id, permanent.code());
        } catch (RuntimeException failure) {
            jobs.failOrRetry(id, workerId, "JOB_EXECUTION_FAILED", false);
            meters.counter("skillstack.jobs.failed", "code", "JOB_EXECUTION_FAILED").increment();
            log.error("Background job failed jobId={} code=JOB_EXECUTION_FAILED", id, failure);
        }
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void cleanup() {
        var deleted = jobs.cleanup();
        if (deleted > 0) log.info("Expired background job metadata deleted count={}", deleted);
    }
}
