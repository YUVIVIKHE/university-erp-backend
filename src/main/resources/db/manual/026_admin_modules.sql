-- ============================================================
-- 026: Admin modules - notices, library, institution settings
-- Safe to re-run (IF NOT EXISTS everywhere).
-- ============================================================

CREATE TABLE IF NOT EXISTS notices (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    audience    VARCHAR(20)  NOT NULL DEFAULT 'ALL'
                CHECK (audience IN ('ALL','STUDENTS','FACULTY','STAFF')),
    priority    VARCHAR(20)  NOT NULL DEFAULT 'NORMAL'
                CHECK (priority IN ('NORMAL','IMPORTANT','URGENT')),
    pinned      BOOLEAN      NOT NULL DEFAULT FALSE,
    publish_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ,
    created_by  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (expires_at IS NULL OR expires_at > publish_at)
);
CREATE INDEX IF NOT EXISTS idx_notices_publish ON notices (pinned DESC, publish_at DESC);

CREATE TABLE IF NOT EXISTS library_books (
    id               BIGSERIAL PRIMARY KEY,
    accession_no     VARCHAR(40)  NOT NULL UNIQUE,
    isbn             VARCHAR(20),
    title            VARCHAR(255) NOT NULL,
    author           VARCHAR(255) NOT NULL,
    publisher        VARCHAR(255),
    category         VARCHAR(100),
    shelf            VARCHAR(50),
    total_copies     INTEGER      NOT NULL CHECK (total_copies >= 0),
    available_copies INTEGER      NOT NULL CHECK (available_copies >= 0),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (available_copies <= total_copies)
);
CREATE INDEX IF NOT EXISTS idx_library_books_title ON library_books (LOWER(title));

CREATE TABLE IF NOT EXISTS library_issues (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT       NOT NULL REFERENCES library_books(id) ON DELETE RESTRICT,
    student_id  BIGINT       NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    issue_date  DATE         NOT NULL,
    due_date    DATE         NOT NULL,
    return_date DATE,
    fine        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (fine >= 0),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED','RETURNED')),
    remarks     VARCHAR(500),
    issued_by   BIGINT REFERENCES users(id) ON DELETE SET NULL,
    returned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (due_date >= issue_date),
    CHECK (return_date IS NULL OR return_date >= issue_date)
);
CREATE INDEX IF NOT EXISTS idx_library_issues_status ON library_issues (status, due_date);
CREATE INDEX IF NOT EXISTS idx_library_issues_student ON library_issues (student_id);
-- A student cannot hold two copies of the same title at once
CREATE UNIQUE INDEX IF NOT EXISTS uq_library_issue_open
    ON library_issues (book_id, student_id) WHERE status = 'ISSUED';

CREATE TABLE IF NOT EXISTS institution_settings (
    setting_key   VARCHAR(60) PRIMARY KEY,
    setting_value TEXT        NOT NULL,
    updated_by    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
