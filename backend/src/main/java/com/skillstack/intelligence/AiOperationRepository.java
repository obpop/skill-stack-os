package com.skillstack.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AiOperationRepository extends JpaRepository<AiOperation, UUID> {
    List<AiOperation> findByStatusAndCreatedAtBefore(AiOperation.Status status, OffsetDateTime before);
    @Query(value = "select 1 from pg_advisory_xact_lock(7345533)", nativeQuery = true)
    Integer lockCostBudget();

    long countByOwnerSubjectAndCreatedAtGreaterThanEqual(String subject, OffsetDateTime since);

    @Query("select coalesce(sum(a.reservedCostUsd), 0) from AiOperation a where a.createdAt >= :since")
    BigDecimal reservedCostSince(@Param("since") OffsetDateTime since);

    @Query("select coalesce(sum(a.reservedCostUsd), 0) from AiOperation a")
    BigDecimal reservedCostTotal();

    @Query("select coalesce(sum(a.estimatedCostUsd), 0) from AiOperation a")
    BigDecimal estimatedCostTotal();
}

