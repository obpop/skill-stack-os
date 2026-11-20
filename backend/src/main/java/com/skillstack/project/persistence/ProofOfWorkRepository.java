package com.skillstack.project.persistence;

import com.skillstack.project.domain.ProofOfWork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProofOfWorkRepository extends JpaRepository<ProofOfWork, UUID> {
    List<ProofOfWork> findAllByOwnerSubjectOrderByCreatedAtAsc(String subject);
    List<ProofOfWork> findAllByProjectIdAndOwnerSubject(UUID projectId, String subject);
    Optional<ProofOfWork> findByIdAndOwnerSubject(UUID id, String subject);
    long countByProjectIdAndOwnerSubject(UUID projectId, String subject);
}
