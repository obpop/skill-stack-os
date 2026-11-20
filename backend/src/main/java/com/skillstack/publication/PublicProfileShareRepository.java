package com.skillstack.publication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicProfileShareRepository extends JpaRepository<PublicProfileShare, UUID> {
    List<PublicProfileShare> findAllByProfileOwnerSubjectOrderByCreatedAtDesc(String subject);
    Optional<PublicProfileShare> findByIdAndProfileOwnerSubject(UUID id, String subject);
    Optional<PublicProfileShare> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
