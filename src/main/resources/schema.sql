-- ============================================================
-- University ERP Schema
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL CHECK (role IN ('STUDENT','FACULTY','ADMIN','ACCOUNTANT')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    invitation_pending BOOLEAN NOT NULL DEFAULT FALSE,
    token_version INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE IF NOT EXISTS user_permissions (
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by    BIGINT REFERENCES users(id),
    PRIMARY KEY (user_id, permission_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   BIGINT,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(512),
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS students (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    roll_number     VARCHAR(50) NOT NULL UNIQUE,
    program         VARCHAR(100) NOT NULL,
    semester        SMALLINT NOT NULL CHECK (semester BETWEEN 1 AND 12),
    batch_year      SMALLINT NOT NULL,
    date_of_birth   DATE,
    phone           VARCHAR(20),
    address         TEXT,
    guardian_name   VARCHAR(255),
    guardian_phone  VARCHAR(20),
    enrollment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_email       ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role        ON users(role);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user   ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_students_roll     ON students(roll_number);
CREATE INDEX IF NOT EXISTS idx_students_program  ON students(program);

-- Default permissions
INSERT INTO permissions (name, description) VALUES
    ('VIEW_DASHBOARD',      'Access the main dashboard'),
    ('MANAGE_USERS',        'Create, update, and deactivate users'),
    ('MANAGE_STUDENTS',     'Manage student records'),
    ('VIEW_GRADES',         'View grade records'),
    ('MANAGE_GRADES',       'Create and update grades'),
    ('VIEW_ATTENDANCE',     'View attendance records'),
    ('MANAGE_ATTENDANCE',   'Mark and update attendance'),
    ('VIEW_FEES',           'View fee records'),
    ('MANAGE_FEES',         'Create and update fee records'),
    ('VIEW_AUDIT_LOGS',     'View system audit logs')
ON CONFLICT (name) DO NOTHING;
