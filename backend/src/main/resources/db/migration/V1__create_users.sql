CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'BLOCKED')),
    CONSTRAINT ck_users_email_trimmed CHECK (email = btrim(email) AND email <> ''),
    CONSTRAINT ck_users_phone_number CHECK (phone_number ~ '^\+[1-9][0-9]{1,14}$')
);

-- The database, not an existsByEmail pre-check, arbitrates concurrent duplicates.
-- Also protects direct SQL inserts that bypass Java's lowercase normalization.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
