package com.skillstack.publication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PublicProfileRepository extends JpaRepository<PublicProfile, UUID> {
    Optional<PublicProfile> findByOwnerSubject(String subject);
    Optional<PublicProfile> findBySlugAndStatus(String slug, PublicationStatus status);
    boolean existsBySlugAndOwnerSubjectNot(String slug, String subject);
}
