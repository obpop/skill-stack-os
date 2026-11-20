CREATE TABLE skill_timeline_event (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    occurred_on DATE,
    date_source VARCHAR(30) NOT NULL,
    source_type VARCHAR(40),
    source_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_timeline_event_type CHECK (event_type IN (
        'SKILL_ADDED', 'SKILL_LEVEL_CHANGED', 'INTEREST_ADDED', 'NEXT_SKILL_SELECTED',
        'ACTION_COMPLETED', 'PROJECT_LINKED', 'PROJECT_COMPLETED', 'PROOF_ADDED', 'PERSONAL_MILESTONE'
    )),
    CONSTRAINT ck_timeline_date_source CHECK (date_source IN ('SERVER_RECORDED', 'USER_ENTERED'))
);

CREATE INDEX idx_timeline_owner_created
    ON skill_timeline_event(owner_id, created_at DESC);

CREATE TABLE public_profile (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    slug VARCHAR(60) NOT NULL UNIQUE,
    publication_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    theme VARCHAR(20) NOT NULL DEFAULT 'OBSIDIAN',
    show_display_name BOOLEAN NOT NULL DEFAULT FALSE,
    show_sentence BOOLEAN NOT NULL DEFAULT FALSE,
    show_skill_levels BOOLEAN NOT NULL DEFAULT FALSE,
    show_intersections BOOLEAN NOT NULL DEFAULT FALSE,
    show_next_skill BOOLEAN NOT NULL DEFAULT FALSE,
    show_projects BOOLEAN NOT NULL DEFAULT FALSE,
    show_proof BOOLEAN NOT NULL DEFAULT FALSE,
    show_milestones BOOLEAN NOT NULL DEFAULT FALSE,
    show_rarity BOOLEAN NOT NULL DEFAULT FALSE,
    selected_skill_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_strategy_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_project_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_proof_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_milestone_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    public_snapshot JSONB,
    published_at TIMESTAMPTZ,
    unpublished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_public_profile_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_public_profile_theme CHECK (theme IN ('OBSIDIAN', 'PAPER', 'SUNRISE')),
    CONSTRAINT ck_public_profile_snapshot CHECK (
        (publication_status = 'DRAFT' AND public_snapshot IS NULL)
        OR (publication_status = 'PUBLISHED' AND public_snapshot IS NOT NULL AND published_at IS NOT NULL)
    )
);

CREATE INDEX idx_public_profile_status_slug
    ON public_profile(publication_status, slug);

CREATE TABLE public_profile_share (
    id UUID PRIMARY KEY,
    public_profile_id UUID NOT NULL REFERENCES public_profile(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_hint VARCHAR(8) NOT NULL,
    label VARCHAR(80),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_public_share_profile_created
    ON public_profile_share(public_profile_id, created_at DESC);

INSERT INTO skill_timeline_event (
    id, owner_id, event_type, title, description, occurred_on, date_source,
    source_type, source_id, created_at, updated_at, version
)
SELECT gen_random_uuid(), owner_id,
       CASE WHEN node_type = 'SKILL' THEN 'SKILL_ADDED' ELSE 'INTEREST_ADDED' END,
       CASE WHEN node_type = 'SKILL' THEN 'Added skill: ' ELSE 'Added interest: ' END || title,
       CASE WHEN node_type = 'SKILL' AND skill_level IS NOT NULL
            THEN 'Initial self-assessed level: ' || replace(initcap(lower(skill_level)), '_', ' ')
            ELSE NULL END,
       NULL, 'SERVER_RECORDED', node_type, id, created_at, created_at, 0
FROM graph_node
WHERE node_type IN ('SKILL', 'INTEREST');

INSERT INTO skill_timeline_event (
    id, owner_id, event_type, title, description, occurred_on, date_source,
    source_type, source_id, created_at, updated_at, version
)
SELECT gen_random_uuid(), owner_id, 'PROJECT_LINKED', 'Added project: ' || title,
       'Project stage: ' || replace(initcap(lower(project_status)), '_', ' '),
       started_at,
       CASE WHEN started_at IS NULL THEN 'SERVER_RECORDED' ELSE 'USER_ENTERED' END,
       'PROJECT', id, created_at, created_at, 0
FROM project;

INSERT INTO skill_timeline_event (
    id, owner_id, event_type, title, description, occurred_on, date_source,
    source_type, source_id, created_at, updated_at, version
)
SELECT gen_random_uuid(), owner_id, 'PROOF_ADDED', 'Added proof: ' || title,
       description, NULL, 'SERVER_RECORDED', 'PROOF', id, created_at, created_at, 0
FROM proof_of_work;

CREATE OR REPLACE FUNCTION record_graph_timeline_event() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.node_type = 'SKILL' THEN
        INSERT INTO skill_timeline_event (
            id, owner_id, event_type, title, description, occurred_on, date_source,
            source_type, source_id, created_at, updated_at, version
        ) VALUES (
            gen_random_uuid(), NEW.owner_id, 'SKILL_ADDED', 'Added skill: ' || NEW.title,
            CASE WHEN NEW.skill_level IS NULL THEN NULL
                 ELSE 'Initial self-assessed level: ' || replace(initcap(lower(NEW.skill_level)), '_', ' ') END,
            NULL, 'SERVER_RECORDED', 'SKILL', NEW.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
        );
    ELSIF TG_OP = 'INSERT' AND NEW.node_type = 'INTEREST' THEN
        INSERT INTO skill_timeline_event (
            id, owner_id, event_type, title, description, occurred_on, date_source,
            source_type, source_id, created_at, updated_at, version
        ) VALUES (
            gen_random_uuid(), NEW.owner_id, 'INTEREST_ADDED', 'Added interest: ' || NEW.title,
            NULL, NULL, 'SERVER_RECORDED', 'INTEREST', NEW.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
        );
    ELSIF TG_OP = 'UPDATE' AND NEW.node_type = 'SKILL' AND OLD.skill_level IS DISTINCT FROM NEW.skill_level THEN
        INSERT INTO skill_timeline_event (
            id, owner_id, event_type, title, description, occurred_on, date_source,
            source_type, source_id, created_at, updated_at, version
        ) VALUES (
            gen_random_uuid(), NEW.owner_id, 'SKILL_LEVEL_CHANGED', 'Updated skill level: ' || NEW.title,
            'Changed from ' || coalesce(replace(initcap(lower(OLD.skill_level)), '_', ' '), 'Unspecified') ||
            ' to ' || coalesce(replace(initcap(lower(NEW.skill_level)), '_', ' '), 'Unspecified') ||
            '. This is a self-assessment, not a certification.',
            NULL, 'SERVER_RECORDED', 'SKILL', NEW.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_graph_timeline_event
AFTER INSERT OR UPDATE OF skill_level ON graph_node
FOR EACH ROW EXECUTE FUNCTION record_graph_timeline_event();

CREATE OR REPLACE FUNCTION record_project_timeline_event() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO skill_timeline_event (
            id, owner_id, event_type, title, description, occurred_on, date_source,
            source_type, source_id, created_at, updated_at, version
        ) VALUES (
            gen_random_uuid(), NEW.owner_id, 'PROJECT_LINKED', 'Added project: ' || NEW.title,
            'Project stage: ' || replace(initcap(lower(NEW.project_status)), '_', ' '),
            NEW.started_at,
            CASE WHEN NEW.started_at IS NULL THEN 'SERVER_RECORDED' ELSE 'USER_ENTERED' END,
            'PROJECT', NEW.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
        );
    ELSIF TG_OP = 'UPDATE' AND OLD.project_status IS DISTINCT FROM NEW.project_status AND NEW.project_status = 'COMPLETED' THEN
        INSERT INTO skill_timeline_event (
            id, owner_id, event_type, title, description, occurred_on, date_source,
            source_type, source_id, created_at, updated_at, version
        ) VALUES (
            gen_random_uuid(), NEW.owner_id, 'PROJECT_COMPLETED', 'Completed project: ' || NEW.title,
            'Completion records progress; it is not external verification.',
            NEW.completed_at,
            CASE WHEN NEW.completed_at IS NULL THEN 'SERVER_RECORDED' ELSE 'USER_ENTERED' END,
            'PROJECT', NEW.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_project_timeline_event
AFTER INSERT OR UPDATE OF project_status ON project
FOR EACH ROW EXECUTE FUNCTION record_project_timeline_event();

CREATE OR REPLACE FUNCTION record_proof_timeline_event() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO skill_timeline_event (
        id, owner_id, event_type, title, description, occurred_on, date_source,
        source_type, source_id, created_at, updated_at, version
    ) VALUES (
        gen_random_uuid(), NEW.owner_id, 'PROOF_ADDED', 'Added proof: ' || NEW.title,
        NEW.description, NULL, 'SERVER_RECORDED', 'PROOF', NEW.id,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_proof_timeline_event
AFTER INSERT ON proof_of_work
FOR EACH ROW EXECUTE FUNCTION record_proof_timeline_event();
