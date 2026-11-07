-- Application intake only. Document verification is not financial approval or receipt of funds.
CREATE TABLE IF NOT EXISTS finance_scholarship_applications (
 id UUID PRIMARY KEY, request_id UUID NOT NULL UNIQUE,
 admission_id VARCHAR(30) NOT NULL REFERENCES admissions(admission_id),
 scheme VARCHAR(150) NOT NULL, category VARCHAR(30) NOT NULL CHECK(category IN ('SCHOLARSHIP','CONCESSION')),
 funding VARCHAR(30) NOT NULL CHECK(funding IN ('INSTITUTION','EXTERNAL')),
 academic_year INTEGER NOT NULL CHECK(academic_year BETWEEN 2000 AND 9999),
 requested_amount NUMERIC(12,2) NOT NULL CHECK(requested_amount>0),
 evidence_reference VARCHAR(250) NOT NULL, reason VARCHAR(2000) NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED' CHECK(status IN ('SUBMITTED','VERIFIED','REJECTED','CANCELLED')),
 created_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 reviewed_by VARCHAR(255), reviewed_at TIMESTAMPTZ, review_note VARCHAR(2000)
);
CREATE INDEX IF NOT EXISTS idx_finance_scholarship_status ON finance_scholarship_applications(status,created_at DESC,id);
CREATE INDEX IF NOT EXISTS idx_finance_scholarship_account ON finance_scholarship_applications(admission_id,created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_finance_scholarship_open ON finance_scholarship_applications(admission_id,lower(scheme),academic_year,category)
 WHERE status IN ('SUBMITTED','VERIFIED');
