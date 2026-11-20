package com.skillstack.stack;

import com.skillstack.graph.domain.GraphNode;
import com.skillstack.graph.domain.NodeStatus;
import com.skillstack.graph.domain.NodeType;
import com.skillstack.graph.domain.Provenance;
import com.skillstack.graph.domain.SkillLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillStackRulesTest {
    @Test
    void rarityUsesTheDocumentedThoughtExperimentAndCapsAtThreeStrongSkills() {
        assertThat(SkillStackRules.rarity(0)).contains("Get one skill");
        assertThat(SkillStackRules.rarity(1)).isEqualTo("1 in 4");
        assertThat(SkillStackRules.rarity(2)).isEqualTo("1 in 16");
        assertThat(SkillStackRules.rarity(7)).isEqualTo("1 in 64");
        assertThat(SkillStackRules.RARITY_CAVEAT).contains("Not a measured population probability");
    }

    @Test
    void quadrantsAreDeterministic() {
        assertThat(SkillStackRules.quadrant(true, true)).isEqualTo("RARE_PAIR");
        assertThat(SkillStackRules.quadrant(true, false)).isEqualTo("PASSION_PROJECT");
        assertThat(SkillStackRules.quadrant(false, true)).isEqualTo("GRIND");
        assertThat(SkillStackRules.quadrant(false, false)).isEqualTo("DEAD_END");
    }

    @Test
    void projectEvidenceCanMakeAStackSkillStrongWithoutMutatingItsChosenLevel() {
        var skill = skill("Writing", 9, SkillLevel.LEARNING);
        assertThat(SkillStackRules.isStrong(skill, false)).isFalse();
        assertThat(SkillStackRules.isStrong(skill, true)).isTrue();
        assertThat(skill.getSkillLevel()).isEqualTo(SkillLevel.LEARNING);
    }

    @Test
    void profileSentenceIsStableAndImportanceOrdered() {
        assertThat(SkillStackRules.sentence(List.of(
                skill("Design", 7, SkillLevel.GOOD),
                skill("Writing", 9, SkillLevel.TOP_25),
                skill("Research", 8, SkillLevel.GOOD))))
                .isEqualTo("Someone who combines Writing, Research and Design.");
    }

    private GraphNode skill(String title, int importance, SkillLevel level) {
        var node = new GraphNode(null, null, NodeType.SKILL, title, null, "ideas",
                (short) importance, (short) 5, NodeStatus.ACTIVE, Provenance.USER_CREATED,
                false, null, null, (short) 2026);
        node.updateSkillStack(level, false, null, false);
        return node;
    }
}
