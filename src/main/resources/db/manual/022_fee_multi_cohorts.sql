-- Add multi-semester/batch scopes without rewriting existing plan or run filters.
CREATE TABLE IF NOT EXISTS finance_fee_structure_semesters (
 structure_id BIGINT NOT NULL REFERENCES finance_fee_structures(id),
 semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 100),PRIMARY KEY(structure_id,semester)
);
CREATE TABLE IF NOT EXISTS finance_fee_structure_batches (
 structure_id BIGINT NOT NULL REFERENCES finance_fee_structures(id),
 batch_year INTEGER NOT NULL CHECK(batch_year BETWEEN 2000 AND 2200),PRIMARY KEY(structure_id,batch_year)
);
CREATE TABLE IF NOT EXISTS finance_fee_assignment_run_batches (
 run_id UUID NOT NULL REFERENCES finance_fee_assignment_runs(id),
 batch_year INTEGER NOT NULL CHECK(batch_year BETWEEN 2000 AND 2200),PRIMARY KEY(run_id,batch_year)
);
