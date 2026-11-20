package com.skillstack.intelligence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderExecutorTest {
    @Test
    void returnsStructuredFixtureOutputWithinDeadline() {
        var executor = new AiProviderExecutor(new AiPolicy(true, 1, 5, 1, 1_000, 2, 0.05, 500));
        var expected = new AiProvider.AiResponse("Headline", "Summary", "Why", Map.of(), 1, 1, 2, BigDecimal.ZERO);
        var provider = provider(() -> expected);
        try {
            assertThat(executor.analyze(provider, request())).isSameAs(expected);
        } finally {
            executor.close();
        }
    }

    @Test
    void cancelsAndClassifiesAProviderThatExceedsTheDeadline() {
        var executor = new AiProviderExecutor(new AiPolicy(true, 1, 5, 1, 1_000, 2, 0.05, 100));
        var provider = provider(() -> {
            Thread.sleep(5_000);
            throw new AssertionError("Interrupted provider should not complete");
        });
        try {
            assertThatThrownBy(() -> executor.analyze(provider, request()))
                    .isInstanceOf(AiProviderExecutor.AiProviderFailure.class)
                    .extracting(problem -> ((AiProviderExecutor.AiProviderFailure) problem).code())
                    .isEqualTo("AI_PROVIDER_TIMEOUT");
        } finally {
            executor.close();
        }
    }

    @Test
    void slowFixtureLoadCoversRepresentativeContextSizesAndConcurrentUsers() throws Exception {
        var executor = new AiProviderExecutor(new AiPolicy(true, 1, 5, 1, 20_000, 20, 0.05, 1_000));
        var provider = provider(() -> {
            Thread.sleep(75);
            return new AiProvider.AiResponse("Headline", "Summary", "Why", Map.of(), 40, 20, 75, BigDecimal.ZERO);
        });
        var sizes = List.of(1, 3, 20, 100);
        var durations = Collections.synchronizedList(new ArrayList<Long>());
        try (var callers = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int repeat = 0; repeat < 4; repeat++) {
                for (var size : sizes) futures.add(callers.submit(() -> {
                    var started = System.nanoTime();
                    executor.analyze(provider, request(size));
                    durations.add((System.nanoTime() - started) / 1_000_000);
                }));
            }
            for (var future : futures) future.get();
        } finally {
            executor.close();
        }
        durations.sort(Comparator.naturalOrder());
        var p50 = durations.get((int) Math.ceil(durations.size() * 0.50) - 1);
        var p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        System.out.printf("PHASE4_SLOW_PROVIDER profiles=%s calls=%d concurrent_users=4 p50_ms=%d p95_ms=%d errors=0%n",
                sizes, durations.size(), p50, p95);
        assertThat(durations).hasSize(16);
    }

    private AiProvider provider(CheckedSupplier<AiProvider.AiResponse> response) {
        return new AiProvider() {
            @Override public String providerId() { return "fixture"; }
            @Override public String modelId() { return "fixture-v1"; }
            @Override public boolean available() { return true; }
            @Override public AiResponse analyze(AiRequest request) {
                try { return response.get(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new RuntimeException(interrupted); }
            }
        };
    }

    private AiProvider.AiRequest request() {
        return new AiProvider.AiRequest("fingerprint", List.of(), List.of(), List.of(), List.of(), List.of(), "Writing", 1_000);
    }

    private AiProvider.AiRequest request(int skills) {
        return new AiProvider.AiRequest("fingerprint-" + skills, List.of(), Collections.nCopies(skills, "skill"),
                List.of(), List.of(), List.of(), "Writing", 20_000);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws InterruptedException; }
}
