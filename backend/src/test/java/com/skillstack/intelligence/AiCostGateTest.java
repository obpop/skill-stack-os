package com.skillstack.intelligence;

import org.junit.jupiter.api.Test;
import com.skillstack.user.AppUser;
import com.skillstack.user.UserService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiCostGateTest {
    private final AiProvider provider = new AiProvider() {
        @Override public String providerId() { return "fixture"; }
        @Override public String modelId() { return "fixture-v1"; }
        @Override public boolean available() { return true; }
        @Override public AiResponse analyze(AiRequest request) { throw new UnsupportedOperationException(); }
    };

    @Test
    void zeroBudgetAlwaysBlocksProviderCalls() {
        var operations = mock(AiOperationRepository.class);
        var policy = new AiPolicy(true, 0, 5, 1, 12_000, 20, 0.05, 8_000);
        var gate = new AiCostGate(policy, operations);

        assertThat(gate.mayCall("local:user", provider)).isFalse();
        assertThat(gate.state("local:user", provider)).isEqualTo("BUDGET_BLOCKED_USD_0");
    }

    @Test
    void monthlySpendReservationAndPerUserQuotaAreHardStops() {
        var operations = mock(AiOperationRepository.class);
        when(operations.reservedCostSince(any(OffsetDateTime.class))).thenReturn(new BigDecimal("0.98"));
        when(operations.reservedCostTotal()).thenReturn(new BigDecimal("0.98"));
        when(operations.countByOwnerSubjectAndCreatedAtGreaterThanEqual(any(String.class), any(OffsetDateTime.class))).thenReturn(2L);
        var budgetGate = new AiCostGate(new AiPolicy(true, 1, 5, 1, 12_000, 20, 0.05, 8_000), operations);
        assertThat(budgetGate.mayCall("local:user", provider)).isFalse();
        assertThat(budgetGate.state("local:user", provider)).isEqualTo("MONTHLY_BUDGET_EXHAUSTED");

        var quotaGate = new AiCostGate(new AiPolicy(true, 10, 5, 1, 12_000, 2, 0.05, 8_000), operations);
        assertThat(quotaGate.mayCall("local:user", provider)).isFalse();
        assertThat(quotaGate.state("local:user", provider)).isEqualTo("USER_QUOTA_EXHAUSTED");
    }

    @Test
    void cumulativeTotalReservationStopsCallsAcrossMonthBoundaries() {
        var operations = mock(AiOperationRepository.class);
        when(operations.reservedCostSince(any(OffsetDateTime.class))).thenReturn(BigDecimal.ZERO);
        when(operations.reservedCostTotal()).thenReturn(new BigDecimal("4.96"));
        var gate = new AiCostGate(new AiPolicy(true, 5, 5, 1, 12_000, 20, 0.05, 8_000), operations);

        assertThat(gate.mayCall("local:user", provider)).isFalse();
        assertThat(gate.state("local:user", provider)).isEqualTo("TOTAL_BUDGET_EXHAUSTED");
    }

    @Test
    void reservationIsPersistedBeforeProviderWorkAndRemainsReservedUntilCompleted() {
        var operations = mock(AiOperationRepository.class);
        var users = mock(UserService.class);
        when(users.require("local:user")).thenReturn(new AppUser("local:user", "Fixture"));
        when(operations.reservedCostSince(any(OffsetDateTime.class))).thenReturn(BigDecimal.ZERO);
        when(operations.reservedCostTotal()).thenReturn(BigDecimal.ZERO);
        var reservation = new AiReservationService(
                new AiPolicy(true, 5, 5, 1, 12_000, 20, 0.05, 8_000), operations, users);

        var id = reservation.reserve("local:user", provider, "CANONICAL_RECOMMENDATION",
                "a".repeat(64), "[]").orElseThrow();
        var operation = org.mockito.ArgumentCaptor.forClass(AiOperation.class);
        verify(operations).saveAndFlush(operation.capture());
        assertThat(operation.getValue().getId()).isEqualTo(id);
        assertThat(operation.getValue().getStatus()).isEqualTo(AiOperation.Status.RESERVED);

        when(operations.findById(id)).thenReturn(Optional.of(operation.getValue()));
        reservation.cancel(id, "PROCESS_INTERRUPTED");
        assertThat(operation.getValue().getStatus()).isEqualTo(AiOperation.Status.CANCELLED);
        assertThat(operation.getValue().getErrorCode()).isEqualTo("PROCESS_INTERRUPTED");
    }
}
