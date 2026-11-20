package com.skillstack.intelligence;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "skillstack.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class AiReservationRecovery {
    private static final Logger log = LoggerFactory.getLogger(AiReservationRecovery.class);
    private final AiReservationService reservations;
    private final MeterRegistry meters;

    public AiReservationRecovery(AiReservationService reservations, MeterRegistry meters) {
        this.reservations = reservations;
        this.meters = meters;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void recover() {
        var count = reservations.recoverAbandoned();
        if (count == 0) return;
        meters.counter("skillstack.ai.reservations.recovered").increment(count);
        log.warn("Recovered abandoned AI reservations count={}", count);
    }
}
