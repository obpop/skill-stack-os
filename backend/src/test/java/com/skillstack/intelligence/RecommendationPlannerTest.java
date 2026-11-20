package com.skillstack.intelligence;

import com.skillstack.profile.CareerRole;
import com.skillstack.profile.SkillDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationPlannerTest {
    @Test
    void twelveSyntheticProfilesProduceGroundedDistinctStrategies() {
        var scenarios = List.of(
                scenario("Engineer bridge", "Java", "engineering", "Finance", "economics", CareerRole.EMPLOYEE, SkillDirection.COMBINE),
                scenario("One-skill student", "Writing", "communication", null, null, CareerRole.STUDENT, SkillDirection.NOT_SURE),
                scenario("Employee growth", "Operations", "business", "Communication", "communication", CareerRole.EMPLOYEE, SkillDirection.DEEPEN_FIELD),
                scenario("Founder", "Software", "engineering", "Product", "business", CareerRole.ENTREPRENEUR, SkillDirection.COMBINE),
                scenario("Freelancer", "Design", "ideas", "Copywriting", "communication", CareerRole.FREELANCER, SkillDirection.COMBINE),
                scenario("Creative student", "Video", "culture", "Writing", "communication", CareerRole.STUDENT, SkillDirection.COMBINE),
                scenario("Career switcher", "Teaching", "education", "Research", "ideas", CareerRole.EMPLOYEE, SkillDirection.SWITCH_FIELD),
                scenario("Side business", "Analytics", "business", "Sales", "business", CareerRole.EMPLOYEE, SkillDirection.COMBINE),
                scenario("No city", "Python", "engineering", "Data Analysis", "business", CareerRole.EMPLOYEE, SkillDirection.SPECIALIZE),
                scenario("Passion", "Music", "culture", "Sound Design", "culture", CareerRole.FREELANCER, SkillDirection.DEEPEN_FIELD),
                scenario("Rejected path", "Marketing", "business", "Writing", "communication", CareerRole.FREELANCER, SkillDirection.COMBINE),
                scenario("Contradictory import", "Java", "engineering", "Painting", "culture", CareerRole.EMPLOYEE, SkillDirection.NOT_SURE)
        );

        for (var scenario : scenarios) {
            var rejected = scenario.name.equals("Rejected path") ? Set.of(IntelligenceDtos.Strategy.INCOME_STACK) : Set.<IntelligenceDtos.Strategy>of();
            var strategies = RecommendationPlanner.strategies(scenario.skills, scenario.profile, "Evidence design", rejected);
            assertThat(strategies).as(scenario.name).hasSizeBetween(2, 3);
            assertThat(strategies.stream().filter(IntelligenceDtos.StrategyOption::recommended)).as(scenario.name).hasSize(1);
            assertThat(strategies).allSatisfy(strategy -> {
                assertThat(strategy.evidence()).isNotEmpty();
                assertThat(strategy.firstAction()).contains("Evidence design");
                assertThat(strategy.limitations()).isNotEmpty();
            });
            assertThat(strategies.stream().map(IntelligenceDtos.StrategyOption::strategy).distinct().count()).isEqualTo(strategies.size());
        }
    }

    @Test
    void providerOutputRejectsUnknownReferencesMarkupAndUrls() {
        var skill = new RecommendationPlanner.SkillSignal(UUID.randomUUID(), "Java", "engineering", "GOOD", 8, true, false);
        var strategies = RecommendationPlanner.strategies(List.of(skill), null, "System Design", Set.of());
        var request = new AiProvider.AiRequest("fingerprint", List.of(skill.id()), List.of("Java"), List.of(), List.of(), strategies, "System Design", 4000);
        var valid = new AiProvider.AiResponse("Build on Java", "A bounded grounded summary.", "It closes a named gap.",
                Map.of(strategies.getFirst().id(), "A more specific explanation."), 100, 50, 200, BigDecimal.ZERO);
        assertThat(AiResponseValidator.validate(valid, request)).isSameAs(valid);

        assertThatThrownBy(() -> AiResponseValidator.validate(new AiProvider.AiResponse(
                "Build on Java", "Visit https://invented.invalid", "Why", Map.of(), 1, 1, 1, BigDecimal.ZERO), request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiResponseValidator.validate(new AiProvider.AiResponse(
                "Build on Java", "Summary", "Why", Map.of("foreign-strategy", "Text"), 1, 1, 1, BigDecimal.ZERO), request))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiResponseValidator.validate(new AiProvider.AiResponse(
                "Build on Java — fast", "Summary", "Why", Map.of(), 1, 1, 1, BigDecimal.ZERO), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("writing contract");
        assertThatThrownBy(() -> AiResponseValidator.validate(new AiProvider.AiResponse(
                "Build on Java", "Here's the truth: furthermore, this changes everything.", "Why", Map.of(), 1, 1, 1, BigDecimal.ZERO), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("writing contract");
    }

    @Test
    void importOutputRequiresQuotedSourceEvidenceAndConservativeLevels() {
        var request = new AiProvider.ImportRequest(
                "I use Java to build billing tools. Project: Renewal dashboard", 10, 120);
        var candidate = new AiProvider.ImportCandidate(
                "SKILL", "Java", "GOOD", "use Java to build billing tools", "Level is inferred and needs review.");
        var valid = new AiProvider.ImportResponse(List.of(candidate), 20, 10, 50, BigDecimal.ZERO);
        assertThat(AiResponseValidator.validateImport(valid, request)).containsExactly(candidate);

        var inventedEvidence = new AiProvider.ImportCandidate(
                "SKILL", "Java", "GOOD", "Java expert for ten years", "Needs review.");
        assertThatThrownBy(() -> AiResponseValidator.validateImport(
                new AiProvider.ImportResponse(List.of(inventedEvidence), 1, 1, 1, BigDecimal.ZERO), request))
                .isInstanceOf(IllegalArgumentException.class);

        var overclaimedLevel = new AiProvider.ImportCandidate(
                "SKILL", "Java", "TOP_25", "use Java", "Needs review.");
        assertThatThrownBy(() -> AiResponseValidator.validateImport(
                new AiProvider.ImportResponse(List.of(overclaimedLevel), 1, 1, 1, BigDecimal.ZERO), request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Scenario scenario(String name, String first, String firstDomain, String second, String secondDomain,
                                     CareerRole role, SkillDirection direction) {
        var skills = new ArrayList<RecommendationPlanner.SkillSignal>();
        skills.add(new RecommendationPlanner.SkillSignal(UUID.nameUUIDFromBytes((name + first).getBytes()), first, firstDomain, "GOOD", 8, true, false));
        if (second != null) skills.add(new RecommendationPlanner.SkillSignal(UUID.nameUUIDFromBytes((name + second).getBytes()), second, secondDomain, "LEARNING", 7, false, false));
        return new Scenario(name, skills, new RecommendationPlanner.ProfileSignal(role, role, direction, (short) 2, name.equals("Side business"), "REMOTE", "BUSINESSES"));
    }

    private record Scenario(String name, List<RecommendationPlanner.SkillSignal> skills,
                            RecommendationPlanner.ProfileSignal profile) {}
}

