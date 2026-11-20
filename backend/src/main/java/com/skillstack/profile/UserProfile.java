package com.skillstack.profile;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "user_profile")
public class UserProfile extends AuditedEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Column(length = 120) private String city;
    @Column(length = 120) private String country;
    @Enumerated(EnumType.STRING) @Column(name = "current_career_role", length = 30) private CareerRole currentRole;
    @Enumerated(EnumType.STRING) @Column(name = "target_role", length = 30) private CareerRole targetRole;
    @Column(name = "build_on_the_side", nullable = false) private boolean buildOnTheSide;
    @Column(name = "timeline_years") private Short timelineYears;
    @Enumerated(EnumType.STRING) @Column(name = "skill_direction", length = 30) private SkillDirection skillDirection;
    @Column(name = "focus_skill_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String focusSkillIds = "[]";
    @Enumerated(EnumType.STRING) @Column(name = "work_preference", length = 30) private WorkPreference workPreference;
    @Enumerated(EnumType.STRING) @Column(name = "help_target", length = 30) private HelpTarget helpTarget;
    @Column(name = "onboarding_completed_at") private OffsetDateTime onboardingCompletedAt;

    protected UserProfile() {}

    public UserProfile(AppUser user) {
        this.user = user;
        this.displayName = user.getDisplayName();
    }

    public void update(String displayName, String city, String country, CareerRole currentRole,
                       CareerRole targetRole, boolean buildOnTheSide, Short timelineYears,
                       SkillDirection skillDirection, String focusSkillIds,
                       WorkPreference workPreference, HelpTarget helpTarget, boolean complete) {
        this.displayName = displayName.trim();
        this.city = blankToNull(city);
        this.country = blankToNull(country);
        this.currentRole = currentRole;
        this.targetRole = targetRole;
        this.buildOnTheSide = buildOnTheSide;
        this.timelineYears = timelineYears;
        this.skillDirection = skillDirection;
        this.focusSkillIds = focusSkillIds;
        this.workPreference = workPreference;
        this.helpTarget = helpTarget;
        if (complete && onboardingCompletedAt == null) onboardingCompletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public AppUser getUser() { return user; }
    public String getDisplayName() { return displayName; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public CareerRole getCurrentRole() { return currentRole; }
    public CareerRole getTargetRole() { return targetRole; }
    public boolean isBuildOnTheSide() { return buildOnTheSide; }
    public Short getTimelineYears() { return timelineYears; }
    public SkillDirection getSkillDirection() { return skillDirection; }
    public String getFocusSkillIds() { return focusSkillIds; }
    public WorkPreference getWorkPreference() { return workPreference; }
    public HelpTarget getHelpTarget() { return helpTarget; }
    public OffsetDateTime getOnboardingCompletedAt() { return onboardingCompletedAt; }
}
