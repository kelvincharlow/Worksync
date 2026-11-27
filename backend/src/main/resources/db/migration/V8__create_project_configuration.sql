-- Projects-owned optional feature settings. No rows means disabled; Overview/Planning stay mandatory.
CREATE TABLE project_enabled_views (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    view VARCHAR(16) NOT NULL CHECK (view IN ('LIST','BOARD')),
    PRIMARY KEY (project_id, view)
);
CREATE TABLE project_enabled_work_types (
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    work_type VARCHAR(16) NOT NULL CHECK (work_type IN ('EPIC','STORY','TASK','BUG')),
    PRIMARY KEY (project_id, work_type)
);
