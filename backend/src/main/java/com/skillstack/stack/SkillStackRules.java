package com.skillstack.stack;

import com.skillstack.graph.domain.GraphNode;
import com.skillstack.graph.domain.SkillLevel;

import java.util.*;

public final class SkillStackRules {
    public static final String RARITY_CAVEAT = "A thought experiment based on 1/4 per strong skill. Not a measured population probability; skills may be correlated.";
    private SkillStackRules() {}

    public static String rarity(int strongSkills) {
        return switch (Math.min(3, Math.max(0, strongSkills))) {
            case 0 -> "Get one skill to the top 25% to start your stack.";
            case 1 -> "1 in 4";
            case 2 -> "1 in 16";
            default -> "1 in 64";
        };
    }

    public static String quadrant(boolean energyHigh, boolean demandHigh) {
        if (energyHigh && demandHigh) return "RARE_PAIR";
        if (energyHigh) return "PASSION_PROJECT";
        if (demandHigh) return "GRIND";
        return "DEAD_END";
    }

    public static String sentence(List<GraphNode> skills) {
        var names = skills.stream().sorted(Comparator.comparingInt(GraphNode::getImportance).reversed()
                        .thenComparing(GraphNode::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(3).map(GraphNode::getTitle).toList();
        if (names.isEmpty()) return "Add a skill to begin your Skill Stack.";
        if (names.size() == 1) return "Someone building skill in " + names.getFirst() + ".";
        return "Someone who combines " + String.join(", ", names.subList(0, names.size() - 1))
                + " and " + names.getLast() + ".";
    }

    public static boolean isStrong(GraphNode node, boolean proven) {
        return proven || node.getSkillLevel() == SkillLevel.TOP_25;
    }
}
