package com.skillstack.publication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, UUID> {
    List<TimelineEvent> findAllByOwnerSubjectOrderByCreatedAtDesc(String subject);
    Optional<TimelineEvent> findByIdAndOwnerSubject(UUID id, String subject);
    boolean existsByOwnerSubjectAndTypeAndSourceId(String subject, TimelineEventType type, UUID sourceId);
}
