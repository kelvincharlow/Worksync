-- V6: Work Items base schema, Links graph, and Bug tracking foundation
CREATE TABLE work_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    type VARCHAR(16) NOT NULL CHECK (type IN ('EPIC', 'STORY', 'TASK', 'BUG')),
    title VARCHAR(255) NOT NULL CHECK (btrim(title) <> ''),
    description TEXT,
    status VARCHAR(32) NOT NULL CHECK (
        status IN (
            'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE',
            'REPORTED', 'TRIAGE', 'ASSIGNED', 'READY_FOR_VERIFICATION',
            'CLOSED', 'REOPENED', 'INVALID', 'DUPLICATE'
        )
    ),
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')
    ),
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assignee_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    parent_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Composite unique constraint to guarantee parent and child share the same project
    CONSTRAINT ux_work_items_project_item UNIQUE (project_id, id),
    CONSTRAINT ck_work_items_no_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT fk_work_items_parent_same_project FOREIGN KEY (project_id, parent_id)
        REFERENCES work_items(project_id, id) ON DELETE RESTRICT
);

-- Work item relationship graph (blocks, blocked_by, relates_to, duplicate_of)
CREATE TABLE work_item_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    source_id UUID NOT NULL REFERENCES work_items(id) ON DELETE RESTRICT,
    target_id UUID NOT NULL REFERENCES work_items(id) ON DELETE RESTRICT,
    link_type VARCHAR(32) NOT NULL CHECK (
        link_type IN ('BLOCKS', 'BLOCKED_BY', 'RELATES_TO', 'DUPLICATE_OF')
    ),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_work_item_links_unique UNIQUE (source_id, target_id, link_type),
    CONSTRAINT ck_work_item_links_no_self_link CHECK (source_id <> target_id)
);

-- Specialized bug tracking metadata
CREATE TABLE bug_details (
    work_item_id UUID PRIMARY KEY REFERENCES work_items(id) ON DELETE RESTRICT,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    affected_area VARCHAR(100) NOT NULL CHECK (btrim(affected_area) <> ''),
    reproduction_steps TEXT NOT NULL CHECK (btrim(reproduction_steps) <> ''),
    expected_result TEXT NOT NULL CHECK (btrim(expected_result) <> ''),
    actual_result TEXT NOT NULL CHECK (btrim(actual_result) <> ''),
    triage_outcome VARCHAR(32),
    triage_reason TEXT,
    fixer_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    verifier_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_bug_details_verifier_not_fixer CHECK (
        verifier_id IS NULL OR fixer_id IS NULL OR verifier_id <> fixer_id
    )
);

-- Record of verification attempts (PASS / FAIL)
CREATE TABLE bug_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bug_id UUID NOT NULL REFERENCES work_items(id) ON DELETE RESTRICT,
    verifier_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    result VARCHAR(16) NOT NULL CHECK (result IN ('PASS', 'FAIL')),
    notes TEXT,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Query performance indexes
CREATE INDEX ix_work_items_project ON work_items(project_id);
CREATE INDEX ix_work_items_project_status ON work_items(project_id, status);
CREATE INDEX ix_work_items_project_assignee ON work_items(project_id, assignee_id);
CREATE INDEX ix_work_items_parent ON work_items(parent_id);
CREATE INDEX ix_work_item_links_project ON work_item_links(project_id);
CREATE INDEX ix_work_item_links_source ON work_item_links(source_id);
CREATE INDEX ix_work_item_links_target ON work_item_links(target_id);
CREATE INDEX ix_bug_details_fixer ON bug_details(fixer_id);
CREATE INDEX ix_bug_details_verifier ON bug_details(verifier_id);
CREATE INDEX ix_bug_verifications_bug ON bug_verifications(bug_id);