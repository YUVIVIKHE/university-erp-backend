BEGIN;
ALTER TABLE finance_operating_entries ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finance_operating_entries ADD COLUMN IF NOT EXISTS progress VARCHAR(20) NOT NULL DEFAULT 'READY';
ALTER TABLE finance_operating_entries ADD COLUMN IF NOT EXISTS target_date DATE;
ALTER TABLE finance_operating_entries ADD COLUMN IF NOT EXISTS evidence_reference VARCHAR(250);
UPDATE finance_operating_entries SET progress='COMPLETED' WHERE status='SETTLED' AND progress='READY';
ALTER TABLE finance_budgets ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finance_vendors ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finance_vendors ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE finance_vendors ADD COLUMN IF NOT EXISTS address VARCHAR(500);
CREATE TABLE IF NOT EXISTS finance_record_events (
 id BIGSERIAL PRIMARY KEY,kind VARCHAR(20) NOT NULL,record_id UUID NOT NULL,
 action VARCHAR(40) NOT NULL,actor VARCHAR(255) NOT NULL,note VARCHAR(2000) NOT NULL,
 previous_values JSONB,created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_finance_record_history ON finance_record_events(kind,record_id,id DESC);
CREATE TABLE IF NOT EXISTS finance_user_profiles (
 user_id BIGINT PRIMARY KEY REFERENCES users(id),phone VARCHAR(30) NOT NULL DEFAULT '',
 designation VARCHAR(100) NOT NULL DEFAULT '',department VARCHAR(120) NOT NULL DEFAULT '',
 office VARCHAR(150) NOT NULL DEFAULT '',version BIGINT NOT NULL DEFAULT 1,updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMIT;
