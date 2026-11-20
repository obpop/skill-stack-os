package com.skillstack.stack;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class StackDtos {
    private StackDtos() {}

    public record Pattern(String label, String evidence) {}
    public record Quadrant(String id, String title, String label, boolean energyHigh, boolean demandHigh,
                           List<String> signals, List<UUID> skillIds) {}
    public record Summary(int strongSkillCount, String illustrativeRarity, String rarityExplanation,
                          String sentence, List<Pattern> patterns, String recommendedStage,
                          String stageReason, List<Quadrant> quadrants) {}

    public record DiscoverRequest(
            @NotEmpty @Size(min = 2, max = 3) List<UUID> skillIds,
            @Size(max = 10) List<UUID> interestIds
    ) {}
    public record Discovery(String id, String title, String why, String build, List<String> learn,
                            String career, Quadrant quadrant, List<UUID> sourceSkillIds) {}

    public record NextSkill(String title, boolean alreadyOwned, List<String> reasons,
                            List<String> levers, List<UUID> sourceSkillIds) {}
    public record Recommendation(String id, String section, String subsection, String title, String reason,
                                 String costLabel, String formatLabel, String lever, String url) {}
    public record Develop(NextSkill nextSkill, List<Recommendation> education, List<Recommendation> networks,
                          List<Recommendation> moves, List<Recommendation> build, List<String> caveats) {}
}
