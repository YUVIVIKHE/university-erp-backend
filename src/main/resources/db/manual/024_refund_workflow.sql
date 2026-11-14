BEGIN;
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS sequence_number BIGSERIAL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_sequence ON finance_refunds(sequence_number);
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS category VARCHAR(40) NOT NULL DEFAULT 'OTHER';
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS request_source VARCHAR(30) NOT NULL DEFAULT 'ACCOUNTS';
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS evidence_reference VARCHAR(250);
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS target_date DATE;
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS verified_by VARCHAR(255);
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS verification_note VARCHAR(2000);
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
ALTER TABLE finance_refunds ADD COLUMN IF NOT EXISTS payout_progress VARCHAR(20) NOT NULL DEFAULT 'READY';
UPDATE finance_refunds SET payout_progress='COMPLETED' WHERE status='PAID' AND payout_progress='READY';
CREATE TABLE IF NOT EXISTS finance_refund_events (
 id BIGSERIAL PRIMARY KEY,refund_id UUID NOT NULL REFERENCES finance_refunds(id),
 action VARCHAR(40) NOT NULL,actor VARCHAR(255) NOT NULL,note VARCHAR(2000) NOT NULL,
 previous_values JSONB,created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refund_events ON finance_refund_events(refund_id,id DESC);
CREATE INDEX IF NOT EXISTS idx_refund_progress ON finance_refunds(status,payout_progress,target_date);
COMMIT;
