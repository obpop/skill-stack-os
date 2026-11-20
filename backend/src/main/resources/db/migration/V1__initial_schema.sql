CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    subject VARCHAR(200) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    onboarding_complete BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE graph_node (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    external_key VARCHAR(180),
    node_type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(2000),
    domain VARCHAR(60) NOT NULL,
    importance SMALLINT NOT NULL CHECK (importance BETWEEN 1 AND 10),
    depth SMALLINT NOT NULL CHECK (depth BETWEEN 1 AND 10),
    status VARCHAR(30) NOT NULL,
    provenance VARCHAR(30) NOT NULL,
    proof_of_work BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    layout_x DOUBLE PRECISION,
    layout_y DOUBLE PRECISION,
    occurred_year SMALLINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_node_owner_external UNIQUE (owner_id, external_key)
);

CREATE TABLE graph_edge (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source_node_id UUID NOT NULL REFERENCES graph_node(id) ON DELETE CASCADE,
    target_node_id UUID NOT NULL REFERENCES graph_node(id) ON DELETE CASCADE,
    relationship_type VARCHAR(40) NOT NULL,
    strength NUMERIC(4,3) NOT NULL CHECK (strength > 0 AND strength <= 1),
    provenance VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_edge_not_self CHECK (source_node_id <> target_node_id),
    CONSTRAINT uq_edge_relationship UNIQUE (owner_id, source_node_id, target_node_id, relationship_type)
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    graph_node_id UUID NOT NULL UNIQUE REFERENCES graph_node(id) ON DELETE CASCADE,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(2000),
    project_status VARCHAR(30) NOT NULL,
    started_at DATE,
    completed_at DATE,
    repository_url VARCHAR(1000),
    live_url VARCHAR(1000),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE proof_of_work (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    title VARCHAR(180) NOT NULL,
    proof_type VARCHAR(40) NOT NULL,
    description VARCHAR(2000),
    url VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE intersection_decision (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    definition_key VARCHAR(180) NOT NULL,
    title VARCHAR(180) NOT NULL,
    decision VARCHAR(30) NOT NULL,
    contributing_node_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    resulting_project_id UUID REFERENCES project(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_intersection_owner_definition UNIQUE (owner_id, definition_key)
);

CREATE INDEX idx_node_owner ON graph_node(owner_id);
CREATE INDEX idx_node_owner_type ON graph_node(owner_id, node_type);
CREATE INDEX idx_node_owner_domain ON graph_node(owner_id, domain);
CREATE INDEX idx_node_owner_title_lower ON graph_node(owner_id, lower(title));
CREATE INDEX idx_edge_owner ON graph_edge(owner_id);
CREATE INDEX idx_edge_source ON graph_edge(source_node_id);
CREATE INDEX idx_edge_target ON graph_edge(target_node_id);
CREATE INDEX idx_project_owner ON project(owner_id);
CREATE INDEX idx_proof_owner_project ON proof_of_work(owner_id, project_id);

CREATE OR REPLACE FUNCTION enforce_edge_ownership() RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM graph_node WHERE id = NEW.source_node_id AND owner_id = NEW.owner_id)
       OR NOT EXISTS (SELECT 1 FROM graph_node WHERE id = NEW.target_node_id AND owner_id = NEW.owner_id) THEN
        RAISE EXCEPTION 'Edge endpoints must belong to edge owner';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_edge_ownership
BEFORE INSERT OR UPDATE ON graph_edge
FOR EACH ROW EXECUTE FUNCTION enforce_edge_ownership();

CREATE OR REPLACE FUNCTION enforce_project_ownership() RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM graph_node WHERE id = NEW.graph_node_id AND owner_id = NEW.owner_id AND node_type = 'PROJECT') THEN
        RAISE EXCEPTION 'Project node must be a PROJECT owned by project owner';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_project_ownership
BEFORE INSERT OR UPDATE ON project
FOR EACH ROW EXECUTE FUNCTION enforce_project_ownership();

CREATE OR REPLACE FUNCTION enforce_proof_ownership() RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM project WHERE id = NEW.project_id AND owner_id = NEW.owner_id) THEN
        RAISE EXCEPTION 'Proof project must belong to proof owner';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_proof_ownership
BEFORE INSERT OR UPDATE ON proof_of_work
FOR EACH ROW EXECUTE FUNCTION enforce_proof_ownership();
