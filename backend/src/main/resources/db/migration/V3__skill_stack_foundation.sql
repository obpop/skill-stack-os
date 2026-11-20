ALTER TABLE graph_node
    ADD COLUMN skill_level VARCHAR(20),
    ADD COLUMN monetizable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN learn_or_outsource VARCHAR(20),
    ADD COLUMN would_do_for_free BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_node_skill_level CHECK (skill_level IS NULL OR skill_level IN ('LEARNING', 'GOOD', 'TOP_25')),
    ADD CONSTRAINT ck_node_learn_or_outsource CHECK (learn_or_outsource IS NULL OR learn_or_outsource IN ('LEARN', 'OUTSOURCE'));

UPDATE graph_node
SET skill_level = 'LEARNING'
WHERE node_type = 'SKILL' AND skill_level IS NULL;

CREATE TABLE user_profile (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    city VARCHAR(120),
    country VARCHAR(120),
    current_career_role VARCHAR(30),
    target_role VARCHAR(30),
    build_on_the_side BOOLEAN NOT NULL DEFAULT FALSE,
    timeline_years SMALLINT,
    skill_direction VARCHAR(30),
    focus_skill_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    work_preference VARCHAR(30),
    help_target VARCHAR(30),
    onboarding_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_profile_role CHECK (current_career_role IS NULL OR current_career_role IN ('STUDENT', 'EMPLOYEE', 'ENTREPRENEUR', 'FREELANCER')),
    CONSTRAINT ck_profile_target_role CHECK (target_role IS NULL OR target_role IN ('STUDENT', 'EMPLOYEE', 'ENTREPRENEUR', 'FREELANCER')),
    CONSTRAINT ck_profile_timeline CHECK (timeline_years IS NULL OR timeline_years BETWEEN 1 AND 10),
    CONSTRAINT ck_profile_direction CHECK (skill_direction IS NULL OR skill_direction IN ('DEEPEN_FIELD', 'SPECIALIZE', 'COMBINE', 'SWITCH_FIELD', 'NOT_SURE')),
    CONSTRAINT ck_profile_work CHECK (work_preference IS NULL OR work_preference IN ('STAY_IN_CITY', 'OPEN_TO_MOVE', 'REMOTE')),
    CONSTRAINT ck_profile_help CHECK (help_target IS NULL OR help_target IN ('BUSINESSES', 'CREATORS', 'STUDENTS', 'A_CAUSE'))
);

CREATE TABLE development_item (
    id VARCHAR(100) PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    subcategory VARCHAR(30),
    title VARCHAR(240) NOT NULL,
    url VARCHAR(1000),
    cost VARCHAR(20) NOT NULL,
    format VARCHAR(30) NOT NULL,
    city VARCHAR(120),
    lever VARCHAR(30) NOT NULL,
    related_skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    related_interests JSONB NOT NULL DEFAULT '[]'::jsonb,
    roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    min_timeline_years SMALLINT NOT NULL DEFAULT 1,
    max_timeline_years SMALLINT NOT NULL DEFAULT 10,
    provenance VARCHAR(30) NOT NULL DEFAULT 'DEMO',
    verification_source VARCHAR(1000),
    seed_version VARCHAR(30) NOT NULL,
    dedupe_key VARCHAR(240) NOT NULL UNIQUE,
    CONSTRAINT ck_development_category CHECK (category IN ('EDUCATION', 'NETWORK', 'JOB_TITLE', 'BUILD')),
    CONSTRAINT ck_development_subcategory CHECK (subcategory IS NULL OR subcategory IN ('FORMAL', 'INFORMAL', 'BOOK')),
    CONSTRAINT ck_development_cost CHECK (cost IN ('FREE', 'PAID')),
    CONSTRAINT ck_development_format CHECK (format IN ('ONLINE', 'IN_PERSON')),
    CONSTRAINT ck_development_lever CHECK (lever IN ('INCOME', 'ATTRACTIVENESS', 'LEVERAGE')),
    CONSTRAINT ck_development_timeline CHECK (min_timeline_years BETWEEN 1 AND 10 AND max_timeline_years BETWEEN min_timeline_years AND 10)
);

CREATE INDEX idx_development_category ON development_item(category, subcategory);
CREATE INDEX idx_development_city ON development_item(city);

CREATE TABLE import_job (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    proposed_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    error_code VARCHAR(80),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_import_source CHECK (source_type IN ('TEXT', 'MARKDOWN', 'PDF', 'DOCX')),
    CONSTRAINT ck_import_status CHECK (status IN ('PENDING', 'EXTRACTED', 'REVIEWED', 'FAILED'))
);

CREATE INDEX idx_import_owner_created ON import_job(owner_id, created_at DESC);
