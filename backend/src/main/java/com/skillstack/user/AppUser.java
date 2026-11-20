package com.skillstack.user;

import com.skillstack.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser extends AuditedEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String subject;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "onboarding_complete", nullable = false)
    private boolean onboardingComplete;

    @Column(name = "default_domain", nullable = false, length = 60)
    private String defaultDomain = "ideas";

    protected AppUser() {}

    public AppUser(String subject, String displayName) {
        this.id = UUID.randomUUID();
        this.subject = subject;
        this.displayName = displayName;
    }

    public UUID getId() { return id; }
    public String getSubject() { return subject; }
    public String getDisplayName() { return displayName; }
    public boolean isOnboardingComplete() { return onboardingComplete; }
    public String getDefaultDomain() { return defaultDomain; }
    public void completeOnboarding() { onboardingComplete = true; }
    public void updateDefaultDomain(String defaultDomain) { this.defaultDomain = defaultDomain.trim().toLowerCase(); }
    public void rename(String displayName) { this.displayName = displayName; }
}
