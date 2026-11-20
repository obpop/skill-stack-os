package com.skillstack.intelligence;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class AiCostGate {
    private final AiPolicy policy;
    private final AiOperationRepository operations;

    public AiCostGate(AiPolicy policy, AiOperationRepository operations) {
        this.policy = policy;
        this.operations = operations;
    }

    public boolean mayCall(String subject, AiProvider provider) {
        if (!policy.mayCall(provider) || policy.maxCallsPerUserMonth() == 0) return false;
        // Serialize the check through transaction commit so concurrent requests cannot both spend the same reservation.
        operations.lockCostBudget();
        var since = monthStart();
        if (operations.countByOwnerSubjectAndCreatedAtGreaterThanEqual(subject, since) >= policy.maxCallsPerUserMonth()) return false;
        var reserved = BigDecimal.valueOf(policy.maxEstimatedCostPerCallUsd());
        if (operations.reservedCostSince(since).add(reserved)
                .compareTo(BigDecimal.valueOf(policy.monthlyBudgetUsd())) > 0) return false;
        return operations.reservedCostTotal().add(reserved)
                .compareTo(BigDecimal.valueOf(policy.totalBudgetUsd())) <= 0;
    }

    public String state(String subject, AiProvider provider) {
        if (!policy.enabled()) return "AI_DISABLED_USD_0";
        if (policy.monthlyBudgetUsd() <= 0) return "BUDGET_BLOCKED_USD_0";
        if (policy.totalBudgetUsd() <= 0) return "TOTAL_BUDGET_BLOCKED_USD_0";
        if (!provider.available()) return "PROVIDER_UNAVAILABLE";
        if (policy.maxCallsPerUserMonth() == 0) return "USER_QUOTA_EXHAUSTED";
        var since = monthStart();
        if (operations.countByOwnerSubjectAndCreatedAtGreaterThanEqual(subject, since) >= policy.maxCallsPerUserMonth()) return "USER_QUOTA_EXHAUSTED";
        var reserved = BigDecimal.valueOf(policy.maxEstimatedCostPerCallUsd());
        if (operations.reservedCostSince(since).add(reserved).compareTo(BigDecimal.valueOf(policy.monthlyBudgetUsd())) > 0) return "MONTHLY_BUDGET_EXHAUSTED";
        if (operations.reservedCostTotal().add(reserved).compareTo(BigDecimal.valueOf(policy.totalBudgetUsd())) > 0) return "TOTAL_BUDGET_EXHAUSTED";
        return "BOUNDED_PROVIDER_CALL";
    }

    public Spend spend() {
        return new Spend(operations.reservedCostSince(monthStart()), operations.reservedCostTotal(), operations.estimatedCostTotal());
    }

    public record Spend(BigDecimal monthlyReservedUsd, BigDecimal totalReservedUsd, BigDecimal totalEstimatedUsd) {}

    private OffsetDateTime monthStart() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        return now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
