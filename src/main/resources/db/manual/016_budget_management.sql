CREATE TABLE IF NOT EXISTS finance_budgets (
 id UUID PRIMARY KEY, request_id UUID UNIQUE NOT NULL, request_hash VARCHAR(64) NOT NULL,
 name VARCHAR(150) NOT NULL, period_start DATE NOT NULL, period_end DATE NOT NULL CHECK(period_end>=period_start),
 amount NUMERIC(14,2) NOT NULL CHECK(amount>0), notes VARCHAR(1000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPROVED','REJECTED','CANCELLED')),
 created_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 reviewed_by VARCHAR(255), reviewed_at TIMESTAMPTZ, review_note VARCHAR(2000)
);
CREATE TABLE IF NOT EXISTS finance_budget_lines (
 budget_id UUID NOT NULL REFERENCES finance_budgets(id), category VARCHAR(30) NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0), PRIMARY KEY(budget_id,category)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_budget_name ON finance_budgets(lower(name),period_start,period_end) WHERE status IN('PENDING','APPROVED');
CREATE INDEX IF NOT EXISTS idx_finance_budget_queue ON finance_budgets(status,created_at DESC,id);
-- Run this migration with autocommit, like the earlier concurrent index migrations.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_operating_budget_actual ON finance_operating_entries(category,settlement_date) INCLUDE(amount) WHERE direction='EXPENSE' AND status='SETTLED';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_operating_budget_commitment ON finance_operating_entries(category,document_date) INCLUDE(amount) WHERE direction='EXPENSE' AND status='APPROVED';
