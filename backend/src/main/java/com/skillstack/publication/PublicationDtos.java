package com.skillstack.publication;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PublicationDtos {
    private PublicationDtos() {}

    public record UpdateRequest(
            @NotBlank @Size(min = 3, max = 60)
            @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$") String slug,
            @NotNull PublicTheme theme,
            boolean showDisplayName,
            boolean showSentence,
            boolean showSkillLevels,
            boolean showIntersections,
            boolean showNextSkill,
            boolean showProjects,
            boolean showProof,
            boolean showMilestones,
            boolean showRarity,
            @NotNull @Size(max = 3) List<UUID> selectedSkillIds,
            @NotNull @Size(max = 3) List<@NotBlank @Size(max = 100) String> selectedStrategyIds,
            @NotNull @Size(max = 6) List<UUID> selectedProjectIds,
            @NotNull @Size(max = 12) List<UUID> selectedProofIds,
            @NotNull @Size(max = 20) List<UUID> selectedMilestoneIds,
            @PositiveOrZero long version
    ) {}

    public record Config(
            String slug, PublicTheme theme, PublicationStatus status,
            boolean showDisplayName, boolean showSentence, boolean showSkillLevels,
            boolean showIntersections, boolean showNextSkill, boolean showProjects,
            boolean showProof, boolean showMilestones, boolean showRarity,
            List<UUID> selectedSkillIds, List<String> selectedStrategyIds,
            List<UUID> selectedProjectIds, List<UUID> selectedProofIds,
            List<UUID> selectedMilestoneIds, OffsetDateTime publishedAt,
            OffsetDateTime unpublishedAt, long version
    ) {}

    public record Skill(String name, String level, boolean projectBacked) {}
    public record Intersection(String name, String explanation, String tradeoff, String firstAction) {}
    public record NextSkill(String name, String whyNow, String firstAction, String evidenceOutcome) {}
    public record Proof(String title, String type, String description, String url) {}
    public record Project(
            String title, String description, String stage, LocalDate startedAt, LocalDate completedAt,
            String repositoryUrl, String liveUrl, List<String> skillsApplied, List<Proof> proof
    ) {}
    public record Timeline(
            String type, String title, String description, LocalDate occurredOn,
            OffsetDateTime recordedAt, TimelineDateSource dateSource
    ) {}
    public record PublicView(
            String slug, String displayName, PublicTheme theme, OffsetDateTime publishedAt,
            String sentence, String illustrativeRarity, String rarityExplanation,
            List<Skill> skills, List<Intersection> intersections, NextSkill developmentFocus,
            List<Project> projects, List<Timeline> timeline, List<String> limitations,
            String attribution
    ) {}

    public record TimelineResponse(
            UUID id, TimelineEventType type, String title, String description, LocalDate occurredOn,
            OffsetDateTime recordedAt, TimelineDateSource dateSource, String sourceType
    ) {}

    public record CreateTimelineRequest(
            @NotNull TimelineEventType type,
            @NotBlank @Size(max = 180) String title,
            @Size(max = 1000) String description,
            @NotNull LocalDate occurredOn
    ) {}

    public record ShareSummary(
            UUID id, String label, String tokenHint, OffsetDateTime createdAt, OffsetDateTime revokedAt
    ) {}

    public record CreateShareRequest(@Size(max = 80) String label) {}
    public record ShareCreated(UUID id, String token, String path, String label, OffsetDateTime createdAt) {}

    public record OwnerResponse(
            Config config, PublicView preview, List<TimelineResponse> timeline,
            List<ShareSummary> shares, String publicPath
    ) {}
}
