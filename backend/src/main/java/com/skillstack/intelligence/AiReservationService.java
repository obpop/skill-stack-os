package com.skillstack.intelligence;

import com.skillstack.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiReservationService {
    private final AiPolicy policy;
    private final AiOperationRepository operations;
    private final UserService users;

    public AiReservationService(AiPolicy policy, AiOperationRepository operations, UserService users) {
        this.policy = policy;
        this.operations = operations;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> reserve(String subject, AiProvider provider, String feature,
                                  String fingerprint, String sourceIds) {
        if (!policy.mayCall(provider) || policy.maxCallsPerUserMonth() == 0) return Optional.empty();
        operations.lockCostBudget();
        var since = monthStart();
        if (operations.countByOwnerSubjectAndCreatedAtGreaterThanEqual(subject, since) >= policy.maxCallsPerUserMonth()) return Optional.empty();
        var reserved = BigDecimal.valueOf(policy.maxEstimatedCostPerCallUsd());
        if (operations.reservedCostSince(since).add(reserved).compareTo(BigDecimal.valueOf(policy.monthlyBudgetUsd())) > 0) return Optional.empty();
        if (operations.reservedCostTotal().add(reserved).compareTo(BigDecimal.valueOf(policy.totalBudgetUsd())) > 0) return Optional.empty();
        var id = UUID.randomUUID();
        operations.saveAndFlush(AiOperation.reserved(id, users.require(subject), feature, fingerprint,
                provider.providerId(), provider.modelId(), reserved, sourceIds));
        return Optional.of(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(UUID id, String code) {
        if (id == null) return;
        operations.findById(id).ifPresent(operation -> {
            operation.cancelled(code);
            operations.saveAndFlush(operation);
        });
    }

    @Transactional
    public int recoverAbandoned() {
        var abandoned = operations.findByStatusAndCreatedAtBefore(AiOperation.Status.RESERVED,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        abandoned.forEach(operation -> operation.cancelled("PROCESS_INTERRUPTED"));
        operations.saveAll(abandoned);
        return abandoned.size();
    }

    private OffsetDateTime monthStart() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        return now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
