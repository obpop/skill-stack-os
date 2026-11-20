package com.skillstack.intelligence;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class IntelligenceDtos {
    private IntelligenceDtos() {}

    public enum Engine { DETERMINISTIC, AI_ASSISTED, AI_FALLBACK }
    public enum Decision { UNREVIEWED, SAVED, REJECTED, MODIFIED, HIDDEN, EXPLORED }
    public enum Strategy {
        COMPLEMENTARY, SPECIALIST_AMPLIFIER, BRIDGE_DOMAINS, TECHNICAL_COMMERCIAL,
        CREATOR_DISTRIBUTION, OPERATOR_AUTOMATION, CAREER_PIVOT, DEPTH_BEFORE_BREADTH,
        INCOME_STACK, LEVERAGE_STACK, LEARN_PARTNER_OUTSOURCE, PASSION_TO_PROOF
    }

    public record GenerateRequest(@Size(max = 3) List<UUID> skillIds, boolean refresh) {}
    public record DecisionRequest(@NotNull Decision decision, @Size(max = 100) String selectedStrategyId) {}

    public record SkillContribution(UUID skillId, String title, String contribution, String evidence) {}
    public record EvidenceRef(String provenance, String sourceType, String sourceId, String label, String reason) {}
    public record StrategyOption(
            String id, Strategy strategy, String title, boolean recommended,
            String summary, List<SkillContribution> contributions, String synergy,
            List<String> plausibleApplications, List<String> limitations,
            String missingCapability, String tradeoff, String firstAction,
            List<EvidenceRef> evidence
    ) {}
    public record NextSkillAlternative(String title, String tradeoff, String uncertainty) {}
    public record NextSkill(
            String title, UUID ownedSkillId, String whyNow,
            List<NextSkillAlternative> alternatives, List<String> levers,
            String firstAction, String evidenceOutcome
    ) {}
    public record Development(
            List<String> educationItemIds, List<String> networkItemIds,
            List<String> moves, String buildProjectSuggestion, String firstAction
    ) {}
    public record Why(
            List<EvidenceRef> evidence, List<String> profileFieldsUsed,
            List<String> assumptions, List<String> tradeoffs,
            String resourceVerification, List<String> retrievalReasons
    ) {}
    public record Provenance(
            String schemaVersion, String provider, String model,
            UUID operationId, boolean cacheHit, int boundedContextCharacters,
            int sourceCount, String costState
    ) {}
    public record Recommendation(
            UUID recommendationId, OffsetDateTime generatedAt, String inputVersion,
            Engine engine, String recommendedStrategyId, String headline, String summary,
            List<UUID> contributingSkillIds, List<StrategyOption> strategies,
            NextSkill nextSkill, Development development, Why why,
            Decision userDecision, String selectedStrategyId, Provenance provenance
    ) {}
    public record AiStatus(
            boolean enabled, String provider, String model, double monthlyBudgetUsd, double totalBudgetUsd,
            int maxCallsPerRequest, int maxInputCharacters, int maxCallsPerUserMonth,
            double maxEstimatedCostPerCallUsd, String state,
            double monthlyReservedUsd, double totalReservedUsd, double totalEstimatedUsd
    ) {}
}

