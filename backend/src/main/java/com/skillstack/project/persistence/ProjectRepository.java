package com.skillstack.project.persistence;

import com.skillstack.project.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByOwnerSubjectOrderByCreatedAtAsc(String subject);
    Optional<Project> findByIdAndOwnerSubject(UUID id, String subject);
    Optional<Project> findByGraphNodeIdAndOwnerSubject(UUID graphNodeId, String subject);
}
