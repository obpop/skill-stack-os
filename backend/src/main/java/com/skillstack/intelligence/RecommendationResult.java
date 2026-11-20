package com.skillstack.intelligence;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommendation_result")
public class RecommendationResult extends AuditedEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @Column(name = "input_fingerprint", nullable = false, length = 64) private String inputFingerprint;
    @Column(name = "selection_key", nullable = false, length = 240) private String selectionKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private IntelligenceDtos.Engine engine;
    @Enumerated(EnumType.STRING) @Column(name = "primary_strategy", nullable = false, length = 40) private IntelligenceDtos.Strategy primaryStrategy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private IntelligenceDtos.Decision decision;
    @Column(name = "selected_strategy_id", length = 100) private String selectedStrategyId;
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "CAST(? AS jsonb)") private String resultJson;
    @Column(name = "generated_at", nullable = false) private OffsetDateTime generatedAt;

    protected RecommendationResult() {}

    public RecommendationResult(UUID id, AppUser owner, String inputFingerprint, String selectionKey,
                                IntelligenceDtos.Engine engine, IntelligenceDtos.Strategy primaryStrategy,
                                String resultJson, OffsetDateTime generatedAt) {
        this.id = id;
        this.owner = owner;
        this.inputFingerprint = inputFingerprint;
        this.selectionKey = selectionKey;
        this.engine = engine;
        this.primaryStrategy = primaryStrategy;
        this.resultJson = resultJson;
        this.generatedAt = generatedAt;
        this.decision = IntelligenceDtos.Decision.UNREVIEWED;
    }

    public void decide(IntelligenceDtos.Decision decision, String selectedStrategyId) {
        this.decision = decision;
        this.selectedStrategyId = selectedStrategyId == null || selectedStrategyId.isBlank() ? null : selectedStrategyId.trim();
    }

    public void replace(IntelligenceDtos.Engine engine, IntelligenceDtos.Strategy primaryStrategy,
                        String resultJson, OffsetDateTime generatedAt) {
        this.engine = engine;
        this.primaryStrategy = primaryStrategy;
        this.resultJson = resultJson;
        this.generatedAt = generatedAt;
        this.decision = IntelligenceDtos.Decision.UNREVIEWED;
        this.selectedStrategyId = null;
    }

    public UUID getId() { return id; }
    public String getInputFingerprint() { return inputFingerprint; }
    public String getSelectionKey() { return selectionKey; }
    public IntelligenceDtos.Engine getEngine() { return engine; }
    public IntelligenceDtos.Strategy getPrimaryStrategy() { return primaryStrategy; }
    public IntelligenceDtos.Decision getDecision() { return decision; }
    public String getSelectedStrategyId() { return selectedStrategyId; }
    public String getResultJson() { return resultJson; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
}

