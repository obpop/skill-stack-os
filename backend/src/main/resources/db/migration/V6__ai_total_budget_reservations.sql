ALTER TABLE ai_operation
    ADD COLUMN reserved_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0;

CREATE INDEX idx_ai_operation_created_reserved
    ON ai_operation(created_at, reserved_cost_usd);
