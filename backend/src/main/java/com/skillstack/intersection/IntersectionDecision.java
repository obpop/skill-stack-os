package com.skillstack.intersection;

import com.skillstack.common.AuditedEntity;
import com.skillstack.project.domain.Project;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Entity
@Table(name = "intersection_decision")
public class IntersectionDecision extends AuditedEntity {
    public enum Decision { SAVED, REJECTED, CONVERTED }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "definition_key", nullable = false, length = 180)
    private String definitionKey;

    @Column(nullable = false, length = 180)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Decision decision;

    @Column(name = "contributing_node_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String contributingNodeIds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_project_id")
    private Project resultingProject;

    protected IntersectionDecision() {}

    public IntersectionDecision(AppUser owner, String definitionKey, String title, Decision decision, String contributingNodeIds) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        update(title, decision, contributingNodeIds, null);
        this.definitionKey = definitionKey;
    }

    public void update(String title, Decision decision, String contributingNodeIds, Project project) {
        this.title = title;
        this.decision = decision;
        this.contributingNodeIds = contributingNodeIds;
        this.resultingProject = project;
    }

    public UUID getId() { return id; }
    public String getDefinitionKey() { return definitionKey; }
    public String getTitle() { return title; }
    public Decision getDecision() { return decision; }
    public String getContributingNodeIds() { return contributingNodeIds; }
    public Project getResultingProject() { return resultingProject; }
}
