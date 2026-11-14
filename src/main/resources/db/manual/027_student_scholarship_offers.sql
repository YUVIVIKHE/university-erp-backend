CREATE TABLE IF NOT EXISTS scholarship_offers (
    id                 BIGSERIAL PRIMARY KEY,
    scheme             VARCHAR(150) NOT NULL UNIQUE,
    description        VARCHAR(1000) NOT NULL,
    category           VARCHAR(30) NOT NULL CHECK (category IN ('SCHOLARSHIP','CONCESSION')),
    funding            VARCHAR(30) NOT NULL CHECK (funding IN ('INSTITUTION','EXTERNAL')),
    program            VARCHAR(100),
    min_semester       SMALLINT NOT NULL DEFAULT 1 CHECK (min_semester BETWEEN 1 AND 12),
    max_semester       SMALLINT NOT NULL DEFAULT 12 CHECK (max_semester BETWEEN 1 AND 12),
    min_batch_year     SMALLINT NOT NULL DEFAULT 2000,
    max_batch_year     SMALLINT NOT NULL DEFAULT 2200,
    max_amount         NUMERIC(12,2) NOT NULL CHECK (max_amount > 0),
    academic_year      INTEGER NOT NULL CHECK (academic_year BETWEEN 2000 AND 9999),
    application_deadline DATE,
    eligibility_criteria VARCHAR(1000) NOT NULL,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (min_semester <= max_semester),
    CHECK (min_batch_year <= max_batch_year)
);

CREATE INDEX IF NOT EXISTS idx_scholarship_offers_active_deadline
    ON scholarship_offers (is_active, application_deadline, scheme);
