-- Additive schema only; templates do not generate student charges.
CREATE TABLE IF NOT EXISTS finance_fee_structures (
 id BIGSERIAL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(120) NOT NULL, course_id BIGINT NOT NULL REFERENCES courses(id),
 academic_year INTEGER NOT NULL CHECK (academic_year BETWEEN 2000 AND 2200),
 semester INTEGER NOT NULL CHECK (semester BETWEEN 1 AND 100),
 status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, created_by VARCHAR(255) NOT NULL
);
CREATE TABLE IF NOT EXISTS finance_fee_structure_lines (
 structure_id BIGINT NOT NULL REFERENCES finance_fee_structures(id), line_order INTEGER NOT NULL,
 fee_head VARCHAR(60) NOT NULL, installment VARCHAR(60) NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK (amount>0), due_date DATE NOT NULL,
 PRIMARY KEY (structure_id,line_order)
);
CREATE INDEX IF NOT EXISTS idx_finance_structures_status_id ON finance_fee_structures(status,id DESC);
