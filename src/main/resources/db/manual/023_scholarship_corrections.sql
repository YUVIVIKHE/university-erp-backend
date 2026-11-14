BEGIN;
ALTER TABLE finance_scholarship_applications ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS finance_scholarship_edits (
 id BIGSERIAL PRIMARY KEY, application_id UUID NOT NULL REFERENCES finance_scholarship_applications(id),
 previous_version BIGINT NOT NULL, previous_values JSONB NOT NULL, changed_by VARCHAR(255) NOT NULL,
 note VARCHAR(2000) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_scholarship_edits_application ON finance_scholarship_edits(application_id,id DESC);
CREATE TABLE IF NOT EXISTS finance_award_reversals (
 id UUID PRIMARY KEY, request_id UUID NOT NULL UNIQUE, decision_id UUID NOT NULL REFERENCES finance_award_decisions(id),
 reason VARCHAR(2000) NOT NULL, requested_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(255), review_note VARCHAR(2000), reviewed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_award_reversal_live ON finance_award_reversals(decision_id) WHERE status IN ('PENDING','APPROVED');
CREATE INDEX IF NOT EXISTS idx_award_reversal_queue ON finance_award_reversals(status,created_at,id);
COMMIT;
