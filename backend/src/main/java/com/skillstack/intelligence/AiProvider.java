package com.skillstack.intelligence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AiProvider {
    String providerId();
    String modelId();
    boolean available();
    AiResponse analyze(AiRequest request);

    default boolean supportsImportExtraction() {
        return false;
    }

    default Optional<ImportResponse> extractImport(ImportRequest request) {
        return Optional.empty();
    }

    record AiRequest(
            String inputFingerprint, List<UUID> ownedSkillIds, List<String> skillFacts,
            List<String> profileFacts, List<String> verifiedCatalogIds,
            List<IntelligenceDtos.StrategyOption> deterministicStrategies,
            String deterministicNextSkill, int maxOutputCharacters
    ) {}

    record AiResponse(
            String headline, String summary, String whyNow,
            Map<String, String> strategySummaries,
            int inputTokens, int outputTokens, long latencyMs, BigDecimal estimatedCostUsd
    ) {}

    record ImportRequest(String untrustedText, int maxProposals, int maxEvidenceCharacters) {}

    record ImportCandidate(
            String type, String title, String suggestedLevel,
            String evidenceExcerpt, String uncertainty
    ) {}

    record ImportResponse(
            List<ImportCandidate> proposals,
            int inputTokens, int outputTokens, long latencyMs, BigDecimal estimatedCostUsd
    ) {}
}

