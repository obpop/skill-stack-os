package com.skillstack.intelligence;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.*;

@Component
public class AiProviderExecutor {
    private final AiPolicy policy;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiProviderExecutor(AiPolicy policy) { this.policy = policy; }

    public AiProvider.AiResponse analyze(AiProvider provider, AiProvider.AiRequest request) {
        return bounded(() -> provider.analyze(request));
    }

    public Optional<AiProvider.ImportResponse> extractImport(AiProvider provider, AiProvider.ImportRequest request) {
        return bounded(() -> provider.extractImport(request));
    }

    private <T> T bounded(Callable<T> operation) {
        var future = executor.submit(operation);
        try {
            return future.get(policy.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException problem) {
            future.cancel(true);
            throw new AiProviderFailure("AI_PROVIDER_TIMEOUT", problem);
        } catch (InterruptedException problem) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiProviderFailure("AI_PROVIDER_INTERRUPTED", problem);
        } catch (ExecutionException problem) {
            throw new AiProviderFailure("AI_PROVIDER_FAILED", problem.getCause());
        }
    }

    @PreDestroy
    void close() { executor.shutdownNow(); }

    public static class AiProviderFailure extends RuntimeException {
        private final String code;
        public AiProviderFailure(String code, Throwable cause) { super(code, cause); this.code = code; }
        public String code() { return code; }
    }
}
