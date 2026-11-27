-- V5 belongs to Projects. V6 is reserved for Work Items; V7 for Users permissions.
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    -- Database-only constant used to enforce that the Owner's membership is MANAGER.
    owner_membership_role VARCHAR(16) NOT NULL DEFAULT 'MANAGER'
        CHECK (owner_membership_role = 'MANAGER'),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT,
    requirements TEXT,
    scope TEXT,
    deliverables TEXT,
    success_criteria TEXT,
    start_date DATE,
    due_date DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_projects_dates CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date)
);
CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role VARCHAR(16) NOT NULL CHECK (role IN ('MANAGER', 'MEMBER', 'VIEWER')),
    responsibility VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_project_members_user UNIQUE (project_id, user_id),
    CONSTRAINT ux_project_members_owner_reference UNIQUE (project_id, user_id, role),
    CONSTRAINT ck_project_members_responsibility CHECK (
        (role = 'MEMBER' AND responsibility IS NOT NULL
            AND responsibility IN ('TECHNICAL_LEAD', 'DEVELOPER', 'QA_TESTER'))
        OR (role IN ('MANAGER', 'VIEWER') AND responsibility IS NULL)
    )
);
-- Deferred only to allow project + initial membership insertion in one transaction.
-- Commit cannot leave an Owner without MANAGER membership; concurrent deletes/demotions
-- are also guarded by PostgreSQL FK locking, not an application-only count check.
ALTER TABLE projects ADD CONSTRAINT fk_projects_owner_manager
    FOREIGN KEY (id, owner_user_id, owner_membership_role)
    REFERENCES project_members(project_id, user_id, role)
    ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX ix_projects_owner ON projects(owner_user_id);
CREATE INDEX ix_projects_creator ON projects(creator_user_id);
CREATE INDEX ix_project_members_user ON project_members(user_id);
