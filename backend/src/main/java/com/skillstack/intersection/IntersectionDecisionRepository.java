package com.skillstack.intersection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntersectionDecisionRepository extends JpaRepository<IntersectionDecision, UUID> {
    Optional<IntersectionDecision> findByOwnerSubjectAndDefinitionKey(String subject, String definitionKey);
    List<IntersectionDecision> findAllByOwnerSubject(String subject);
}
