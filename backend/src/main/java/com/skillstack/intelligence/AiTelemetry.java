package com.skillstack.intelligence;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiTelemetry {
    private final MeterRegistry meters;

    public AiTelemetry(MeterRegistry meters) { this.meters = meters; }

    public void cacheHit() { meters.counter("skillstack.ai.cache", "result", "hit").increment(); }

    public void notAdmitted(String state) {
        meters.counter("skillstack.ai.admission", "result", safeState(state)).increment();
    }

    public void attempted() { meters.counter("skillstack.ai.calls", "result", "attempted").increment(); }

    public void succeeded(AiProvider.AiResponse response) {
        meters.counter("skillstack.ai.calls", "result", "succeeded").increment();
        meters.counter("skillstack.ai.tokens", "kind", "input").increment(Math.max(0, response.inputTokens()));
        meters.counter("skillstack.ai.tokens", "kind", "output").increment(Math.max(0, response.outputTokens()));
        if (response.estimatedCostUsd() != null) {
            meters.counter("skillstack.ai.estimated.cost.usd", "feature", "recommendation")
                    .increment(Math.max(0, response.estimatedCostUsd().doubleValue()));
        }
        Timer.builder("skillstack.ai.latency").tag("result", "succeeded").register(meters)
                .record(Duration.ofMillis(Math.max(0, response.latencyMs())));
    }

    public void failed(String code) {
        meters.counter("skillstack.ai.calls", "result", "failed", "code", safeCode(code)).increment();
    }

    private String safeState(String value) {
        return value != null && value.matches("[A-Z0-9_]{1,60}") ? value : "UNKNOWN";
    }

    private String safeCode(String value) {
        return value != null && value.matches("[A-Z0-9_]{1,60}") ? value : "UNKNOWN";
    }
}
