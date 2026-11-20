package com.skillstack.graph.domain;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Entity
@Table(name = "graph_node")
public class GraphNode extends AuditedEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "external_key", length = 180)
    private String externalKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 40)
    private NodeType type;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 60)
    private String domain;

    @Column(nullable = false)
    private short importance;

    @Column(nullable = false)
    private short depth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NodeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Provenance provenance;

    @Column(name = "proof_of_work", nullable = false)
    private boolean proofOfWork;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", length = 20)
    private SkillLevel skillLevel;

    @Column(nullable = false)
    private boolean monetizable;

    @Enumerated(EnumType.STRING)
    @Column(name = "learn_or_outsource", length = 20)
    private LearnOrOutsource learnOrOutsource;

    @Column(name = "would_do_for_free", nullable = false)
    private boolean wouldDoForFree;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)")
    private String metadata = "{}";

    @Column(name = "layout_x")
    private Double layoutX;

    @Column(name = "layout_y")
    private Double layoutY;

    @Column(name = "occurred_year")
    private Short occurredYear;

    protected GraphNode() {}

    public GraphNode(AppUser owner, String externalKey, NodeType type, String title, String description,
                     String domain, short importance, short depth, NodeStatus status, Provenance provenance,
                     boolean proofOfWork, Double layoutX, Double layoutY, Short occurredYear) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.externalKey = externalKey;
        update(type, title, description, domain, importance, depth, status);
        this.provenance = provenance;
        this.proofOfWork = proofOfWork;
        this.layoutX = layoutX;
        this.layoutY = layoutY;
        this.occurredYear = occurredYear;
        updateSkillStack(null, false, null, false);
    }

    public void update(NodeType type, String title, String description, String domain, short importance, short depth, NodeStatus status) {
        this.type = type;
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.domain = domain.trim().toLowerCase();
        this.importance = importance;
        this.depth = depth;
        this.status = status;
    }

    public void markProof() { this.proofOfWork = true; }
    public void clearProof() { this.proofOfWork = false; }
    public void updateSkillStack(SkillLevel skillLevel, boolean monetizable,
                                 LearnOrOutsource learnOrOutsource, boolean wouldDoForFree) {
        if (type == NodeType.SKILL) {
            this.skillLevel = skillLevel == null ? SkillLevel.LEARNING : skillLevel;
            this.monetizable = monetizable;
            this.learnOrOutsource = learnOrOutsource;
            this.wouldDoForFree = false;
        } else if (type == NodeType.INTEREST) {
            this.skillLevel = null;
            this.monetizable = false;
            this.learnOrOutsource = null;
            this.wouldDoForFree = wouldDoForFree;
        } else {
            this.skillLevel = null;
            this.monetizable = false;
            this.learnOrOutsource = null;
            this.wouldDoForFree = false;
        }
    }
    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getExternalKey() { return externalKey; }
    public NodeType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDomain() { return domain; }
    public short getImportance() { return importance; }
    public short getDepth() { return depth; }
    public NodeStatus getStatus() { return status; }
    public Provenance getProvenance() { return provenance; }
    public boolean isProofOfWork() { return proofOfWork; }
    public SkillLevel getSkillLevel() { return skillLevel; }
    public boolean isMonetizable() { return monetizable; }
    public LearnOrOutsource getLearnOrOutsource() { return learnOrOutsource; }
    public boolean isWouldDoForFree() { return wouldDoForFree; }
    public String getMetadata() { return metadata; }
    public Double getLayoutX() { return layoutX; }
    public Double getLayoutY() { return layoutY; }
    public Short getOccurredYear() { return occurredYear; }
}
