package com.skillstack.intelligence;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ai_operation")
public class AiOperation extends AuditedEntity {
    public enum Status { RESERVED, SUCCEEDED, FAILED, CANCELLED, BUDGET_BLOCKED }

    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id") private RecommendationResult recommendation;
    @Column(nullable = false, length = 60) private String feature;
    @Column(name = "input_fingerprint", nullable = false, length = 64) private String inputFingerprint;
    @Column(nullable = false, length = 100) private String provider;
    @Column(nullable = false, length = 120) private String model;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status;
    @Column(name = "input_tokens") private Integer inputTokens;
    @Column(name = "output_tokens") private Integer outputTokens;
    @Column(name = "latency_ms") private Long latencyMs;
    @Column(name = "estimated_cost_usd", precision = 12, scale = 6) private BigDecimal estimatedCostUsd;
    @Column(name = "reserved_cost_usd", nullable = false, precision = 12, scale = 6) private BigDecimal reservedCostUsd;
    @Column(name = "source_ids", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String sourceIds;
    @Column(name = "error_code", length = 80) private String errorCode;

    protected AiOperation() {}

    public AiOperation(UUID id, AppUser owner, RecommendationResult recommendation, String feature, String inputFingerprint,
                       String provider, String model, Status status, Integer inputTokens, Integer outputTokens,
                       Long latencyMs, BigDecimal estimatedCostUsd, BigDecimal reservedCostUsd,
                       String sourceIds, String errorCode) {
        this.id = id;
        this.owner = owner;
        this.recommendation = recommendation;
        this.feature = feature;
        this.inputFingerprint = inputFingerprint;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.estimatedCostUsd = estimatedCostUsd;
        this.reservedCostUsd = reservedCostUsd;
        this.sourceIds = sourceIds;
        this.errorCode = errorCode;
    }

    public static AiOperation reserved(UUID id, AppUser owner, String feature, String inputFingerprint,
                                       String provider, String model, BigDecimal reservedCostUsd, String sourceIds) {
        return new AiOperation(id, owner, null, feature, inputFingerprint, provider, model, Status.RESERVED,
                null, null, null, BigDecimal.ZERO, reservedCostUsd, sourceIds, null);
    }

    public void succeeded(RecommendationResult recommendation, AiProvider.AiResponse response) {
        if (status != Status.RESERVED) return;
        this.recommendation = recommendation;
        status = Status.SUCCEEDED;
        inputTokens = response.inputTokens();
        outputTokens = response.outputTokens();
        latencyMs = response.latencyMs();
        estimatedCostUsd = response.estimatedCostUsd() == null ? BigDecimal.ZERO : response.estimatedCostUsd();
        errorCode = null;
    }

    public void failed(RecommendationResult recommendation, String errorCode) {
        if (status != Status.RESERVED) return;
        this.recommendation = recommendation;
        status = Status.FAILED;
        estimatedCostUsd = BigDecimal.ZERO;
        this.errorCode = errorCode;
    }

    public void cancelled(String errorCode) {
        if (status != Status.RESERVED) return;
        status = Status.CANCELLED;
        estimatedCostUsd = BigDecimal.ZERO;
        this.errorCode = errorCode;
    }

    public UUID getId() { return id; }
    public Status getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}

