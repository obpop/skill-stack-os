package com.skillstack.intelligence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiProviderTest {
    @Test
    void springCanConstructTheEnabledProvider() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "skillstack.ai.provider=openai",
                        "skillstack.ai.enabled=true",
                        "skillstack.ai.monthly-budget-usd=4.90",
                        "skillstack.ai.total-budget-usd=4.90")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(AiPolicy.class, OpenAiProvider.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenAiProvider.class);
                });
    }

    @Test
    void responsesApiRequestIsStructuredStatelessAndParsesUsage() throws Exception {
        var skill = new RecommendationPlanner.SkillSignal(
                UUID.randomUUID(), "Java", "engineering", "GOOD", 8, true, false);
        var strategies = RecommendationPlanner.strategies(List.of(skill), null, "System Design", Set.of());
        var mapper = new ObjectMapper();
        var structured = Map.of(
                "headline", "Build on Java",
                "summary", "Use the existing implementation base to make a focused systems decision.",
                "why_now", "System Design turns the current Java capability into an inspectable architecture choice.",
                "strategy_summaries", List.of(
                        Map.of("strategy_id", "specialist_amplifier", "summary", "Deepen Java while practicing one system boundary."),
                        Map.of("strategy_id", "complementary", "summary", "Add a complementary design skill without claiming it is already owned."),
                        Map.of("strategy_id", "technical_commercial", "summary", "Test whether the system improvement matters to a specific user.")
                )
        );
        var responseBody = mapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(Map.of("type", "message", "content", List.of(
                        Map.of("type", "output_text", "text", mapper.writeValueAsString(structured))))),
                "usage", Map.of("input_tokens", 1000, "output_tokens", 500,
                        "input_tokens_details", Map.of("cached_tokens", 200, "cache_write_tokens", 100))
        ));
        var transport = new CapturingTransport(responseBody);
        var provider = new OpenAiProvider(mapper, transport, "gpt-6-sol", 12_000, 1_400, "low");
        var request = new AiProvider.AiRequest(
                "fingerprint", List.of(skill.id()), List.of("Java: GOOD"), List.of("currentRole=EMPLOYEE"),
                List.of("verified-1"), strategies, "System Design", 4_000);

        var response = AiResponseValidator.validate(provider.analyze(request), request);

        assertThat(response.headline()).isEqualTo("Build on Java");
        assertThat(response.strategySummaries()).containsKey("specialist_amplifier");
        assertThat(response.inputTokens()).isEqualTo(1000);
        assertThat(response.outputTokens()).isEqualTo(500);
        assertThat(response.estimatedCostUsd()).isEqualByComparingTo("0.006690");
        var sent = mapper.readTree(transport.requestBody);
        assertThat(sent.path("model").asText()).isEqualTo("gpt-6-sol");
        assertThat(sent.path("store").asBoolean()).isFalse();
        assertThat(sent.path("reasoning").path("effort").asText()).isEqualTo("low");
        assertThat(sent.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(sent.has("tools")).isFalse();
    }

    @Test
    void pricingRoundsUpSoTheEstimateNeverUnderstatesFractionalCost() {
        assertThat(OpenAiProvider.estimatedCost(1, 0, 0, 1))
                .isEqualByComparingTo(new BigDecimal("0.000012"));
    }

    @Test
    void pilotProviderDoesNotClaimDocumentExtractionCapability() {
        var provider = new OpenAiProvider(new ObjectMapper(), new CapturingTransport("{}"),
                "gpt-6-sol", 12_000, 1_400, "low");

        assertThat(provider.supportsImportExtraction()).isFalse();
    }

    @Test
    void rejectsQuotaAuthMalformedAndEmptyProviderResponsesWithoutExposingBodies() {
        var mapper = new ObjectMapper();
        var request = new AiProvider.AiRequest("fingerprint", List.of(), List.of("Java: GOOD"),
                List.of(), List.of(), List.of(), "System Design", 4_000);
        for (var fixture : List.of(
                new CapturingTransport(429, "quota-private-body"),
                new CapturingTransport(401, "auth-private-body"),
                new CapturingTransport(500, "server-private-body"),
                new CapturingTransport(200, "not-json"),
                new CapturingTransport(200, "{}"))) {
            var provider = new OpenAiProvider(mapper, fixture, "gpt-6-sol", 12_000, 1_400, "low");
            assertThatThrownBy(() -> provider.analyze(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageNotContaining("private-body");
        }
    }

    private static final class CapturingTransport implements OpenAiProvider.Transport {
        private final String responseBody;
        private String requestBody;

        private final int status;

        private CapturingTransport(String responseBody) { this(200, responseBody); }
        private CapturingTransport(int status, String responseBody) { this.status = status; this.responseBody = responseBody; }
        @Override public boolean available() { return true; }
        @Override public OpenAiProvider.TransportResponse send(String requestBody) {
            this.requestBody = requestBody;
            return new OpenAiProvider.TransportResponse(status, responseBody, 321);
        }
    }
}
