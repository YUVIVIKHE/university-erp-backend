-- Run with psql autocommit, without BEGIN/COMMIT (concurrent index below).
CREATE TABLE IF NOT EXISTS finance_fee_assignment_runs (
 id UUID PRIMARY KEY, structure_id BIGINT NOT NULL REFERENCES finance_fee_structures(id),
 created_by VARCHAR(255) NOT NULL, program VARCHAR(100) NOT NULL, batch_year INTEGER,
 status VARCHAR(20) NOT NULL DEFAULT 'PREVIEW', created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 expires_at TIMESTAMPTZ NOT NULL DEFAULT now()+interval '30 minutes'
);
CREATE TABLE IF NOT EXISTS finance_fee_assignment_targets (
 run_id UUID NOT NULL REFERENCES finance_fee_assignment_runs(id),
 admission_id VARCHAR(30) NOT NULL REFERENCES admissions(admission_id),
 state VARCHAR(20) NOT NULL DEFAULT 'READY', reason VARCHAR(255),
 PRIMARY KEY(run_id,admission_id)
);
CREATE INDEX IF NOT EXISTS idx_fee_assignment_targets_state ON finance_fee_assignment_targets(run_id,state,admission_id);
CREATE INDEX IF NOT EXISTS idx_fee_assignment_owner ON finance_fee_assignment_runs(created_by,created_at DESC);
ALTER TABLE fees ADD COLUMN IF NOT EXISTS finance_structure_id BIGINT REFERENCES finance_fee_structures(id);
ALTER TABLE fees ADD COLUMN IF NOT EXISTS finance_structure_line INTEGER;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_fee_structure_charge ON fees(admission_id,finance_structure_id,finance_structure_line);
