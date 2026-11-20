package com.skillstack.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, UUID> {
    Optional<RecommendationResult> findByIdAndOwnerSubject(UUID id, String subject);
    Optional<RecommendationResult> findFirstByOwnerSubjectOrderByGeneratedAtDesc(String subject);
    Optional<RecommendationResult> findByOwnerSubjectAndInputFingerprintAndSelectionKey(String subject, String fingerprint, String selectionKey);
    List<RecommendationResult> findTop20ByOwnerSubjectOrderByGeneratedAtDesc(String subject);
}

