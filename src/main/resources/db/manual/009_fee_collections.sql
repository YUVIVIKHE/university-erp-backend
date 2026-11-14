CREATE TABLE IF NOT EXISTS finance_fee_payments (
 id UUID PRIMARY KEY, sequence_number BIGSERIAL UNIQUE NOT NULL,
 request_id UUID UNIQUE NOT NULL, request_hash VARCHAR(64) NOT NULL,
 receipt_number VARCHAR(40) UNIQUE, admission_id VARCHAR(30) NOT NULL REFERENCES admissions(admission_id),
 student_name VARCHAR(201) NOT NULL, program VARCHAR(100) NOT NULL,
 amount NUMERIC(14,2) NOT NULL CHECK(amount>0), method VARCHAR(20) NOT NULL,
 reference VARCHAR(100), paid_date DATE NOT NULL, notes VARCHAR(1000),
 recorded_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fee_payment_reference ON finance_fee_payments(method,lower(reference)) WHERE reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_fee_payments_admission ON finance_fee_payments(admission_id,sequence_number DESC);
CREATE INDEX IF NOT EXISTS idx_fee_payments_date ON finance_fee_payments(paid_date,sequence_number DESC);
CREATE TABLE IF NOT EXISTS finance_fee_payment_allocations (
 payment_id UUID NOT NULL REFERENCES finance_fee_payments(id),
 fee_id VARCHAR(30) NOT NULL REFERENCES fees(fee_id), fee_type VARCHAR(60) NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0), paid_before NUMERIC(12,2) NOT NULL,
 balance_after NUMERIC(12,2) NOT NULL CHECK(balance_after>=0),
 PRIMARY KEY(payment_id,fee_id)
);
