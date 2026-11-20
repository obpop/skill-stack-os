package com.skillstack.intelligence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiPolicy {
    private final boolean enabled;
    private final double monthlyBudgetUsd;
    private final double totalBudgetUsd;
    private final int maxCallsPerRequest;
    private final int maxInputCharacters;
    private final int maxCallsPerUserMonth;
    private final double maxEstimatedCostPerCallUsd;
    private final int timeoutMs;

    public AiPolicy(
            @Value("${skillstack.ai.enabled:false}") boolean enabled,
            @Value("${skillstack.ai.monthly-budget-usd:0}") double monthlyBudgetUsd,
            @Value("${skillstack.ai.total-budget-usd:0}") double totalBudgetUsd,
            @Value("${skillstack.ai.max-calls-per-request:1}") int maxCallsPerRequest,
            @Value("${skillstack.ai.max-input-characters:12000}") int maxInputCharacters,
            @Value("${skillstack.ai.max-calls-per-user-month:20}") int maxCallsPerUserMonth,
            @Value("${skillstack.ai.max-estimated-cost-per-call-usd:0.05}") double maxEstimatedCostPerCallUsd,
            @Value("${skillstack.ai.timeout-ms:8000}") int timeoutMs) {
        this.enabled = enabled;
        this.monthlyBudgetUsd = Math.max(0, monthlyBudgetUsd);
        this.totalBudgetUsd = Math.max(0, totalBudgetUsd);
        this.maxCallsPerRequest = Math.max(0, Math.min(1, maxCallsPerRequest));
        this.maxInputCharacters = Math.max(1000, Math.min(20_000, maxInputCharacters));
        this.maxCallsPerUserMonth = Math.max(0, Math.min(1_000, maxCallsPerUserMonth));
        this.maxEstimatedCostPerCallUsd = Math.max(0, maxEstimatedCostPerCallUsd);
        this.timeoutMs = Math.max(100, Math.min(30_000, timeoutMs));
    }

    public boolean mayCall(AiProvider provider) {
        return enabled && monthlyBudgetUsd > 0 && totalBudgetUsd > 0
                && maxCallsPerRequest == 1 && provider.available();
    }
    public boolean enabled() { return enabled; }
    public double monthlyBudgetUsd() { return monthlyBudgetUsd; }
    public double totalBudgetUsd() { return totalBudgetUsd; }
    public int maxCallsPerRequest() { return maxCallsPerRequest; }
    public int maxInputCharacters() { return maxInputCharacters; }
    public int maxCallsPerUserMonth() { return maxCallsPerUserMonth; }
    public double maxEstimatedCostPerCallUsd() { return maxEstimatedCostPerCallUsd; }
    public int timeoutMs() { return timeoutMs; }
}

