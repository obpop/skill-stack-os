package com.skillstack.project.domain;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "proof_of_work")
public class ProofOfWork extends AuditedEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 180)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", nullable = false, length = 40)
    private ProofType type;

    @Column(length = 2000)
    private String description;

    @Column(length = 1000)
    private String url;

    protected ProofOfWork() {}

    public ProofOfWork(AppUser owner, Project project, String title, ProofType type, String description, String url) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.project = project;
        this.title = title.trim();
        this.type = type;
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.url = url == null || url.isBlank() ? null : url.trim();
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public Project getProject() { return project; }
    public String getTitle() { return title; }
    public ProofType getType() { return type; }
    public String getDescription() { return description; }
    public String getUrl() { return url; }
}
