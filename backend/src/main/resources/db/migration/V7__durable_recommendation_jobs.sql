ALTER TABLE ai_operation DROP CONSTRAINT ck_ai_operation_status;
ALTER TABLE ai_operation
    ADD CONSTRAINT ck_ai_operation_status
        CHECK (status IN ('RESERVED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'BUDGET_BLOCKED'));

CREATE TABLE background_job (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    operation_type VARCHAR(40) NOT NULL,
    request_key VARCHAR(180) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(30) NOT NULL,
    progress_stage VARCHAR(40) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 2,
    priority SMALLINT NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(120),
    lease_until TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE,
    result_reference_id UUID REFERENCES recommendation_result(id) ON DELETE SET NULL,
    safe_error_code VARCHAR(80),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_background_job_request UNIQUE (owner_id, operation_type, request_key),
    CONSTRAINT ck_background_job_operation CHECK (operation_type IN ('RECOMMENDATION')),
    CONSTRAINT ck_background_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'RETRY_SCHEDULED', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_background_job_stage CHECK (progress_stage IN ('READING_STACK', 'COMPARING_COMBINATIONS', 'CHECKING_CATALOG', 'PREPARING_RECOMMENDATIONS', 'COMPLETED')),
    CONSTRAINT ck_background_job_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 5 AND attempt_count <= max_attempts),
    CONSTRAINT ck_background_job_priority CHECK (priority BETWEEN -10 AND 10),
    CONSTRAINT ck_background_job_lease CHECK (
        (status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR status <> 'RUNNING'
    )
);

CREATE INDEX idx_background_job_claim
    ON background_job(status, available_at, priority DESC, created_at)
    WHERE status IN ('QUEUED', 'RETRY_SCHEDULED');
CREATE INDEX idx_background_job_owner_created
    ON background_job(owner_id, created_at DESC);
CREATE INDEX idx_background_job_running_lease
    ON background_job(lease_until)
    WHERE status = 'RUNNING';

CREATE OR REPLACE FUNCTION enforce_background_job_result_owner()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.result_reference_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM recommendation_result r
        WHERE r.id = NEW.result_reference_id AND r.owner_id = NEW.owner_id
    ) THEN
        RAISE EXCEPTION 'background job result owner mismatch';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_background_job_result_owner
BEFORE INSERT OR UPDATE OF owner_id, result_reference_id ON background_job
FOR EACH ROW EXECUTE FUNCTION enforce_background_job_result_owner();
