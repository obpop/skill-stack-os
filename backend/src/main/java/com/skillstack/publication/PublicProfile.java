package com.skillstack.publication;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "public_profile")
public class PublicProfile extends AuditedEntity {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private AppUser owner;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicTheme theme;

    @Column(name = "show_display_name", nullable = false) private boolean showDisplayName;
    @Column(name = "show_sentence", nullable = false) private boolean showSentence;
    @Column(name = "show_skill_levels", nullable = false) private boolean showSkillLevels;
    @Column(name = "show_intersections", nullable = false) private boolean showIntersections;
    @Column(name = "show_next_skill", nullable = false) private boolean showNextSkill;
    @Column(name = "show_projects", nullable = false) private boolean showProjects;
    @Column(name = "show_proof", nullable = false) private boolean showProof;
    @Column(name = "show_milestones", nullable = false) private boolean showMilestones;
    @Column(name = "show_rarity", nullable = false) private boolean showRarity;

    @Column(name = "selected_skill_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String selectedSkillIds = "[]";
    @Column(name = "selected_strategy_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String selectedStrategyIds = "[]";
    @Column(name = "selected_project_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String selectedProjectIds = "[]";
    @Column(name = "selected_proof_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String selectedProofIds = "[]";
    @Column(name = "selected_milestone_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String selectedMilestoneIds = "[]";

    @Column(name = "public_snapshot", columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String publicSnapshot;
    @Column(name = "published_at") private OffsetDateTime publishedAt;
    @Column(name = "unpublished_at") private OffsetDateTime unpublishedAt;

    protected PublicProfile() {}

    public PublicProfile(AppUser owner, String slug) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.slug = slug;
        this.status = PublicationStatus.DRAFT;
        this.theme = PublicTheme.OBSIDIAN;
    }

    public void configure(String slug, PublicTheme theme,
                          boolean showDisplayName, boolean showSentence, boolean showSkillLevels,
                          boolean showIntersections, boolean showNextSkill, boolean showProjects,
                          boolean showProof, boolean showMilestones, boolean showRarity,
                          String selectedSkillIds, String selectedStrategyIds, String selectedProjectIds,
                          String selectedProofIds, String selectedMilestoneIds) {
        this.slug = slug;
        this.theme = theme;
        this.showDisplayName = showDisplayName;
        this.showSentence = showSentence;
        this.showSkillLevels = showSkillLevels;
        this.showIntersections = showIntersections;
        this.showNextSkill = showNextSkill;
        this.showProjects = showProjects;
        this.showProof = showProof;
        this.showMilestones = showMilestones;
        this.showRarity = showRarity;
        this.selectedSkillIds = selectedSkillIds;
        this.selectedStrategyIds = selectedStrategyIds;
        this.selectedProjectIds = selectedProjectIds;
        this.selectedProofIds = selectedProofIds;
        this.selectedMilestoneIds = selectedMilestoneIds;
        if (status == PublicationStatus.PUBLISHED) {
            status = PublicationStatus.DRAFT;
            publicSnapshot = null;
            unpublishedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

    public void publish(String snapshot, OffsetDateTime publishedAt) {
        this.publicSnapshot = snapshot;
        this.publishedAt = publishedAt;
        this.unpublishedAt = null;
        this.status = PublicationStatus.PUBLISHED;
    }

    public void unpublish(OffsetDateTime unpublishedAt) {
        this.status = PublicationStatus.DRAFT;
        this.publicSnapshot = null;
        this.unpublishedAt = unpublishedAt;
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getSlug() { return slug; }
    public PublicationStatus getStatus() { return status; }
    public PublicTheme getTheme() { return theme; }
    public boolean isShowDisplayName() { return showDisplayName; }
    public boolean isShowSentence() { return showSentence; }
    public boolean isShowSkillLevels() { return showSkillLevels; }
    public boolean isShowIntersections() { return showIntersections; }
    public boolean isShowNextSkill() { return showNextSkill; }
    public boolean isShowProjects() { return showProjects; }
    public boolean isShowProof() { return showProof; }
    public boolean isShowMilestones() { return showMilestones; }
    public boolean isShowRarity() { return showRarity; }
    public String getSelectedSkillIds() { return selectedSkillIds; }
    public String getSelectedStrategyIds() { return selectedStrategyIds; }
    public String getSelectedProjectIds() { return selectedProjectIds; }
    public String getSelectedProofIds() { return selectedProofIds; }
    public String getSelectedMilestoneIds() { return selectedMilestoneIds; }
    public String getPublicSnapshot() { return publicSnapshot; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getUnpublishedAt() { return unpublishedAt; }
}
