-- Additive: original charges and cash receipts are never rewritten.
ALTER TABLE fees ADD COLUMN IF NOT EXISTS concession_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS finance_award_decisions (
 id UUID PRIMARY KEY, application_id UUID NOT NULL UNIQUE REFERENCES finance_scholarship_applications(id),
 request_id UUID NOT NULL UNIQUE, request_hash VARCHAR(64) NOT NULL,
 outcome VARCHAR(20) NOT NULL CHECK(outcome IN ('POSTED','DECLINED')),
 amount NUMERIC(12,2) NOT NULL CHECK(amount>=0), note VARCHAR(2000) NOT NULL,
 decided_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS finance_concession_allocations (
 decision_id UUID NOT NULL REFERENCES finance_award_decisions(id),
 fee_id VARCHAR(30) NOT NULL REFERENCES fees(fee_id), fee_type VARCHAR(60) NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0), balance_before NUMERIC(12,2) NOT NULL,
 balance_after NUMERIC(12,2) NOT NULL, PRIMARY KEY(decision_id,fee_id)
);
CREATE INDEX IF NOT EXISTS idx_finance_concession_fee ON finance_concession_allocations(fee_id,decision_id);
CREATE INDEX IF NOT EXISTS idx_finance_award_created ON finance_award_decisions(created_at DESC,id);
