package com.skillstack.intelligence;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class AiResponseValidator {
    private AiResponseValidator() {}

    public static AiProvider.AiResponse validate(AiProvider.AiResponse response, AiProvider.AiRequest request) {
        if (response == null) throw new IllegalArgumentException("AI response is missing");
        validateRecommendationText(response.headline(), 180, "headline");
        validateRecommendationText(response.summary(), 900, "summary");
        validateRecommendationText(response.whyNow(), 700, "whyNow");
        if (response.strategySummaries() == null || response.strategySummaries().size() > 3) {
            throw new IllegalArgumentException("AI strategy summaries are not bounded");
        }
        var allowed = new HashSet<String>();
        request.deterministicStrategies().forEach(item -> allowed.add(item.id()));
        response.strategySummaries().forEach((id, text) -> {
            if (!allowed.contains(id)) throw new IllegalArgumentException("AI referenced an unknown strategy");
            validateRecommendationText(text, 900, "strategy summary");
        });
        if (response.inputTokens() < 0 || response.outputTokens() < 0 || response.latencyMs() < 0) {
            throw new IllegalArgumentException("AI usage metadata is invalid");
        }
        return response;
    }

    public static List<AiProvider.ImportCandidate> validateImport(
            AiProvider.ImportResponse response, AiProvider.ImportRequest request
    ) {
        if (response == null || response.proposals() == null || response.proposals().size() > request.maxProposals()) {
            throw new IllegalArgumentException("AI import proposals are not bounded");
        }
        if (response.inputTokens() < 0 || response.outputTokens() < 0 || response.latencyMs() < 0) {
            throw new IllegalArgumentException("AI import usage metadata is invalid");
        }
        var source = normalizeWhitespace(request.untrustedText()).toLowerCase(Locale.ROOT);
        response.proposals().forEach(candidate -> {
            if (candidate == null || !List.of("SKILL", "INTEREST", "PROJECT").contains(candidate.type())) {
                throw new IllegalArgumentException("AI import proposal has an invalid type");
            }
            validateText(candidate.title(), 180, "import title");
            if ("SKILL".equals(candidate.type())) {
                if (!List.of("LEARNING", "GOOD").contains(candidate.suggestedLevel())) {
                    throw new IllegalArgumentException("AI may not infer an unsupported skill level");
                }
            } else if (candidate.suggestedLevel() != null) {
                throw new IllegalArgumentException("AI may only suggest a level for a skill");
            }
            validateText(candidate.evidenceExcerpt(), request.maxEvidenceCharacters(), "import evidence");
            if (!source.contains(normalizeWhitespace(candidate.evidenceExcerpt()).toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("AI import evidence is not present in the source text");
            }
            validateText(candidate.uncertainty(), 240, "import uncertainty");
        });
        return List.copyOf(response.proposals());
    }

    private static void validateText(String text, int max, String field) {
        if (text == null || text.isBlank() || text.length() > max) throw new IllegalArgumentException("AI " + field + " is invalid");
        var lower = text.toLowerCase();
        if (text.contains("<") || text.contains(">") || lower.contains("javascript:") || lower.contains("http://") || lower.contains("https://")) {
            throw new IllegalArgumentException("AI output contains unsafe markup or an unverified URL");
        }
    }

    private static void validateRecommendationText(String text, int max, String field) {
        validateText(text, max, field);
        var lower = text.toLowerCase(Locale.ROOT);
        if (text.contains("—") || lower.contains("it's not just") || lower.contains("it is not just")
                || lower.contains("here's the truth") || lower.contains("what nobody tells you")
                || lower.matches(".*\\b(delve|tapestry|unwavering|furthermore|moreover)\\b.*")) {
            throw new IllegalArgumentException("AI output violates the writing contract");
        }
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}

