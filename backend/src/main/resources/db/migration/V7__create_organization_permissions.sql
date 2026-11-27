-- Users owns V7. V6 belongs to Work Items and must be coordinated before shared DB rollout.
CREATE TABLE organization_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    permission VARCHAR(64) NOT NULL,
    granted_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_organization_permissions_user_permission UNIQUE (user_id, permission),
    CONSTRAINT ck_organization_permissions_name CHECK (permission IN ('PROJECT_CREATE'))
);
-- The unique index covers user_id lookup; this index supports the grantor FK.
CREATE INDEX ix_organization_permissions_grantor ON organization_permissions(granted_by_user_id);
