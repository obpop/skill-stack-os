package com.skillstack.project.domain;

import com.skillstack.common.AuditedEntity;
import com.skillstack.graph.domain.GraphNode;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project extends AuditedEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graph_node_id", nullable = false, unique = true)
    private GraphNode graphNode;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_status", nullable = false, length = 30)
    private ProjectStatus status;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @Column(name = "completed_at")
    private LocalDate completedAt;

    @Column(name = "repository_url", length = 1000)
    private String repositoryUrl;

    @Column(name = "live_url", length = 1000)
    private String liveUrl;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String metadata = "{}";

    protected Project() {}

    public Project(AppUser owner, GraphNode graphNode, String title, String description, ProjectStatus status) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.graphNode = graphNode;
        update(title, description, status, null, null, null, null);
    }

    public void update(String title, String description, ProjectStatus status, LocalDate startedAt, LocalDate completedAt,
                       String repositoryUrl, String liveUrl) {
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.repositoryUrl = blankToNull(repositoryUrl);
        this.liveUrl = blankToNull(liveUrl);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public GraphNode getGraphNode() { return graphNode; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }
    public LocalDate getStartedAt() { return startedAt; }
    public LocalDate getCompletedAt() { return completedAt; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getLiveUrl() { return liveUrl; }
}
