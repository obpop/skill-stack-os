package com.skillstack.graph.persistence;

import com.skillstack.graph.domain.GraphEdge;
import com.skillstack.graph.domain.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphEdgeRepository extends JpaRepository<GraphEdge, UUID> {
    List<GraphEdge> findAllByOwnerSubjectOrderByCreatedAtAsc(String subject);
    Optional<GraphEdge> findByIdAndOwnerSubject(UUID id, String subject);
    boolean existsByOwnerSubjectAndSourceIdAndTargetIdAndRelationshipType(String subject, UUID sourceId, UUID targetId, RelationshipType type);
}
