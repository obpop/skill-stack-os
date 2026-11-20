package com.skillstack.graph.domain;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "graph_edge")
public class GraphEdge extends AuditedEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_node_id", nullable = false)
    private GraphNode source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_node_id", nullable = false)
    private GraphNode target;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 40)
    private RelationshipType relationshipType;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal strength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Provenance provenance;

    protected GraphEdge() {}

    public GraphEdge(AppUser owner, GraphNode source, GraphNode target, RelationshipType type, BigDecimal strength, Provenance provenance) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.source = source;
        this.target = target;
        this.relationshipType = type;
        this.strength = strength;
        this.provenance = provenance;
    }

    public void update(RelationshipType type, BigDecimal strength) {
        this.relationshipType = type;
        this.strength = strength;
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public GraphNode getSource() { return source; }
    public GraphNode getTarget() { return target; }
    public RelationshipType getRelationshipType() { return relationshipType; }
    public BigDecimal getStrength() { return strength; }
    public Provenance getProvenance() { return provenance; }
}
