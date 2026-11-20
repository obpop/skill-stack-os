package com.skillstack.intelligence;

import com.skillstack.profile.CareerRole;
import com.skillstack.profile.SkillDirection;

import java.util.*;

public final class RecommendationPlanner {
    private RecommendationPlanner() {}

    public record SkillSignal(UUID id, String title, String domain, String level, int importance, boolean monetizable, boolean proven) {}
    public record ProfileSignal(CareerRole currentRole, CareerRole targetRole, SkillDirection direction,
                                Short timelineYears, boolean buildOnTheSide, String workPreference, String helpTarget) {}

    public static List<IntelligenceDtos.StrategyOption> strategies(List<SkillSignal> skills, ProfileSignal profile,
                                                                    String nextSkill, Set<IntelligenceDtos.Strategy> rejected) {
        if (skills.isEmpty()) return List.of();
        var primary = primaryStrategy(skills, profile);
        if (rejected.contains(primary)) primary = primary == IntelligenceDtos.Strategy.DEPTH_BEFORE_BREADTH
                ? IntelligenceDtos.Strategy.COMPLEMENTARY : IntelligenceDtos.Strategy.DEPTH_BEFORE_BREADTH;
        var recommended = primary;
        var ordered = new ArrayList<IntelligenceDtos.Strategy>();
        ordered.add(primary);
        addDistinct(ordered, skills.stream().map(SkillSignal::domain).distinct().count() > 1
                ? IntelligenceDtos.Strategy.BRIDGE_DOMAINS : IntelligenceDtos.Strategy.COMPLEMENTARY);
        addDistinct(ordered, skills.stream().anyMatch(SkillSignal::monetizable)
                ? IntelligenceDtos.Strategy.TECHNICAL_COMMERCIAL : IntelligenceDtos.Strategy.DEPTH_BEFORE_BREADTH);
        addDistinct(ordered, IntelligenceDtos.Strategy.DEPTH_BEFORE_BREADTH);
        return ordered.stream().limit(3).map(strategy -> option(strategy, strategy == recommended, skills, profile, nextSkill)).toList();
    }

    private static IntelligenceDtos.Strategy primaryStrategy(List<SkillSignal> skills, ProfileSignal profile) {
        if (profile != null && profile.direction() == SkillDirection.SPECIALIZE) return IntelligenceDtos.Strategy.DEPTH_BEFORE_BREADTH;
        if (profile != null && profile.direction() == SkillDirection.SWITCH_FIELD) return IntelligenceDtos.Strategy.CAREER_PIVOT;
        if (profile != null && (profile.currentRole() == CareerRole.ENTREPRENEUR || profile.currentRole() == CareerRole.FREELANCER)) {
            return skills.stream().anyMatch(SkillSignal::monetizable) ? IntelligenceDtos.Strategy.INCOME_STACK : IntelligenceDtos.Strategy.LEARN_PARTNER_OUTSOURCE;
        }
        if (skills.stream().map(SkillSignal::domain).distinct().count() > 1) return IntelligenceDtos.Strategy.BRIDGE_DOMAINS;
        return IntelligenceDtos.Strategy.SPECIALIST_AMPLIFIER;
    }

    private static void addDistinct(List<IntelligenceDtos.Strategy> values, IntelligenceDtos.Strategy value) {
        if (!values.contains(value)) values.add(value);
    }

    private static IntelligenceDtos.StrategyOption option(IntelligenceDtos.Strategy strategy, boolean recommended,
                                                            List<SkillSignal> skills, ProfileSignal profile, String nextSkill) {
        var names = skills.stream().map(SkillSignal::title).toList();
        var joined = join(names);
        var contributions = skills.stream().map(skill -> new IntelligenceDtos.SkillContribution(
                skill.id(), skill.title(), contribution(skill),
                "User fact: " + skill.level().toLowerCase(Locale.ROOT).replace('_', ' ') + ", importance " + skill.importance() + "/10"
        )).toList();
        var title = title(strategy);
        var mechanism = mechanism(strategy, joined, nextSkill);
        var application = application(strategy, joined, profile);
        var tradeoff = tradeoff(strategy);
        var evidence = skills.stream().map(skill -> new IntelligenceDtos.EvidenceRef(
                skill.proven() ? "USER_FACT" : "USER_FACT", "SKILL", skill.id().toString(), skill.title(),
                skill.proven() ? "Owned skill with project-backed evidence" : "Owned self-reported skill"
        )).toList();
        return new IntelligenceDtos.StrategyOption(
                strategy.name().toLowerCase(Locale.ROOT), strategy, title, recommended,
                mechanism, contributions, mechanism, List.of(application),
                List.of("This is a decision hypothesis; usefulness and external demand still need validation."),
                nextSkill, tradeoff,
                "Use " + nextSkill + " in one small, observable project with " + skills.getFirst().title() + ".",
                evidence
        );
    }

    private static String contribution(SkillSignal skill) {
        var strength = skill.proven() ? "project-backed practice" : switch (skill.level()) {
            case "TOP_25" -> "self-reported depth";
            case "GOOD" -> "working capability";
            default -> "an active learning base";
        };
        return skill.title() + " contributes " + strength + " in " + skill.domain() + ".";
    }

    private static String title(IntelligenceDtos.Strategy strategy) {
        return switch (strategy) {
            case COMPLEMENTARY -> "Add a complementary capability";
            case SPECIALIST_AMPLIFIER -> "Deepen a specialty and add an amplifier";
            case BRIDGE_DOMAINS -> "Combine two worlds";
            case TECHNICAL_COMMERCIAL -> "Connect craft to a commercial test";
            case CREATOR_DISTRIBUTION -> "Pair creation with distribution";
            case OPERATOR_AUTOMATION -> "Turn operating knowledge into a repeatable system";
            case CAREER_PIVOT -> "Build a bridge toward the target role";
            case DEPTH_BEFORE_BREADTH -> "Make existing depth more defensible";
            case INCOME_STACK -> "Package the stack as a small offer";
            case LEVERAGE_STACK -> "Turn repeated work into leverage";
            case LEARN_PARTNER_OUTSOURCE -> "Choose what to learn, partner for, or outsource";
            case PASSION_TO_PROOF -> "Turn interest into inspectable proof";
        };
    }

    private static String mechanism(IntelligenceDtos.Strategy strategy, String skills, String nextSkill) {
        return switch (strategy) {
            case BRIDGE_DOMAINS -> skills + " can bridge implementation and domain judgment; " + nextSkill + " addresses the translation gap.";
            case DEPTH_BEFORE_BREADTH -> skills + " becomes more credible when one capability is practiced deeply and evidenced before adding breadth.";
            case TECHNICAL_COMMERCIAL, INCOME_STACK -> skills + " can become economically testable when " + nextSkill + " helps connect the work to a specific problem and audience.";
            case LEARN_PARTNER_OUTSOURCE -> skills + " provides a base; treat " + nextSkill + " as a learn-versus-partner decision rather than assuming every gap must become your skill.";
            case CAREER_PIVOT -> skills + " can transfer through a bridge project; " + nextSkill + " makes the destination capability easier to inspect.";
            default -> skills + " can reinforce one another when " + nextSkill + " fills a concrete capability gap instead of adding unrelated breadth.";
        };
    }

    private static String application(IntelligenceDtos.Strategy strategy, String skills, ProfileSignal profile) {
        var role = profile == null || profile.targetRole() == null ? "a real audience" : profile.targetRole().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return switch (strategy) {
            case INCOME_STACK, TECHNICAL_COMMERCIAL -> "Test a narrowly scoped service or within-role contribution for " + role + ".";
            case CAREER_PIVOT -> "Create a bridge artifact that makes " + skills + " legible to " + role + " work.";
            default -> "Build one small artifact showing how " + skills + " changes an outcome for " + role + ".";
        };
    }

    private static String tradeoff(IntelligenceDtos.Strategy strategy) {
        return switch (strategy) {
            case DEPTH_BEFORE_BREADTH -> "More defensible depth, but slower exploration of a new field.";
            case BRIDGE_DOMAINS -> "More differentiated application, but domain assumptions need early validation.";
            case INCOME_STACK, TECHNICAL_COMMERCIAL -> "Faster economic feedback, but payability remains a hypothesis until tested.";
            case LEARN_PARTNER_OUTSOURCE -> "Faster access to missing capability, but less direct learning and control.";
            case CAREER_PIVOT -> "Preserves transferable strengths, but the target role may require additional credentials or experience.";
            default -> "Broader usefulness, but shallow practice can weaken the combination.";
        };
    }

    private static String join(List<String> values) {
        if (values.size() == 1) return values.getFirst();
        return String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.getLast();
    }
}

