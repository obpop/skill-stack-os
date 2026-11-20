CREATE TABLE recommendation_result (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    input_fingerprint VARCHAR(64) NOT NULL,
    selection_key VARCHAR(240) NOT NULL,
    engine VARCHAR(30) NOT NULL,
    primary_strategy VARCHAR(40) NOT NULL,
    decision VARCHAR(30) NOT NULL DEFAULT 'UNREVIEWED',
    selected_strategy_id VARCHAR(100),
    result_json JSONB NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_recommendation_engine CHECK (engine IN ('DETERMINISTIC', 'AI_ASSISTED', 'AI_FALLBACK')),
    CONSTRAINT ck_recommendation_decision CHECK (decision IN ('UNREVIEWED', 'SAVED', 'REJECTED', 'MODIFIED', 'HIDDEN', 'EXPLORED')),
    CONSTRAINT uq_recommendation_input UNIQUE (owner_id, input_fingerprint, selection_key)
);

CREATE INDEX idx_recommendation_owner_generated
    ON recommendation_result(owner_id, generated_at DESC);

CREATE TABLE ai_operation (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    recommendation_id UUID REFERENCES recommendation_result(id) ON DELETE SET NULL,
    feature VARCHAR(60) NOT NULL,
    input_fingerprint VARCHAR(64) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    latency_ms BIGINT,
    estimated_cost_usd NUMERIC(12, 6),
    source_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_operation_status CHECK (status IN ('SUCCEEDED', 'FAILED', 'BUDGET_BLOCKED'))
);

CREATE INDEX idx_ai_operation_owner_created
    ON ai_operation(owner_id, created_at DESC);

