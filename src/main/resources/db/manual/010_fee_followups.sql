-- Operational follow-ups only; no fee balances or academic permissions change.
CREATE TABLE IF NOT EXISTS finance_fee_followups (
 id BIGSERIAL PRIMARY KEY, request_id UUID UNIQUE NOT NULL,
 admission_id VARCHAR(30) NOT NULL REFERENCES admissions(admission_id),
 channel VARCHAR(20) NOT NULL, outcome VARCHAR(20) NOT NULL,
 note VARCHAR(2000) NOT NULL, promised_date DATE, promised_amount NUMERIC(12,2),
 next_contact_date DATE, recorded_by VARCHAR(255) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_finance_fee_followup_account ON finance_fee_followups(admission_id,id DESC);
