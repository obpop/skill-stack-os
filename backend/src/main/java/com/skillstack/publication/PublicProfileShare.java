package com.skillstack.publication;

import com.skillstack.common.AuditedEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "public_profile_share")
public class PublicProfileShare extends AuditedEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "public_profile_id", nullable = false) private PublicProfile profile;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "token_hint", nullable = false, length = 8) private String tokenHint;
    @Column(length = 80) private String label;
    @Column(name = "revoked_at") private OffsetDateTime revokedAt;

    protected PublicProfileShare() {}

    public PublicProfileShare(PublicProfile profile, String tokenHash, String tokenHint, String label) {
        this.id = UUID.randomUUID();
        this.profile = profile;
        this.tokenHash = tokenHash;
        this.tokenHint = tokenHint;
        this.label = label == null || label.isBlank() ? null : label.trim();
    }

    public void revoke(OffsetDateTime at) { this.revokedAt = at; }
    public UUID getId() { return id; }
    public PublicProfile getProfile() { return profile; }
    public String getTokenHash() { return tokenHash; }
    public String getTokenHint() { return tokenHint; }
    public String getLabel() { return label; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
}
