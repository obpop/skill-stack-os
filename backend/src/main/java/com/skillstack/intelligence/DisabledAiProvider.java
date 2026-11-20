package com.skillstack.intelligence;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "skillstack.ai.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledAiProvider implements AiProvider {
    @Override public String providerId() { return "disabled"; }
    @Override public String modelId() { return "none"; }
    @Override public boolean available() { return false; }
    @Override public AiResponse analyze(AiRequest request) {
        throw new IllegalStateException("No AI provider is configured");
    }
}

