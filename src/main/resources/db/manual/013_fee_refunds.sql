-- Refunds preserve original receipts; only verified disbursement reduces net paid fees.
CREATE TABLE IF NOT EXISTS finance_refunds (
 id UUID PRIMARY KEY, request_id UUID NOT NULL UNIQUE, request_hash VARCHAR(64) NOT NULL,
 payment_id UUID NOT NULL REFERENCES finance_fee_payments(id),
 amount NUMERIC(14,2) NOT NULL CHECK(amount>0), reason VARCHAR(2000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPROVED','REJECTED','CANCELLED','PAID')),
 created_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 reviewed_by VARCHAR(255), reviewed_at TIMESTAMPTZ, review_note VARCHAR(2000),
 payout_request_id UUID UNIQUE, payout_hash VARCHAR(64), payout_method VARCHAR(20), payout_reference VARCHAR(100),
 payout_date DATE, paid_by VARCHAR(255), paid_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS finance_refund_allocations (
 refund_id UUID NOT NULL REFERENCES finance_refunds(id),
 fee_id VARCHAR(30) NOT NULL REFERENCES fees(fee_id), fee_type VARCHAR(60) NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0), paid_before NUMERIC(12,2), paid_after NUMERIC(12,2),
 PRIMARY KEY(refund_id,fee_id)
);
CREATE INDEX IF NOT EXISTS idx_finance_refund_payment ON finance_refunds(payment_id,status);
CREATE INDEX IF NOT EXISTS idx_finance_refund_queue ON finance_refunds(status,created_at DESC,id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_refund_payout_reference ON finance_refunds(payout_method,lower(payout_reference)) WHERE payout_reference IS NOT NULL;
