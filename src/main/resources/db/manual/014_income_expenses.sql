-- Standalone non-fee cash register. Does not duplicate student collections/refunds or payroll.
CREATE TABLE IF NOT EXISTS finance_operating_entries (
 id UUID PRIMARY KEY, request_id UUID UNIQUE NOT NULL, request_hash VARCHAR(64) NOT NULL,
 direction VARCHAR(10) NOT NULL CHECK(direction IN('INCOME','EXPENSE')),
 category VARCHAR(30) NOT NULL, counterparty VARCHAR(150) NOT NULL,
 document_reference VARCHAR(150) NOT NULL, document_date DATE NOT NULL,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0), description VARCHAR(2000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPROVED','REJECTED','CANCELLED','SETTLED')),
 created_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 reviewed_by VARCHAR(255), reviewed_at TIMESTAMPTZ, review_note VARCHAR(2000),
 settlement_request_id UUID UNIQUE, settlement_hash VARCHAR(64), settlement_method VARCHAR(20),
 settlement_reference VARCHAR(100), settlement_date DATE, settled_by VARCHAR(255), settled_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_operating_document ON finance_operating_entries(direction,lower(counterparty),lower(document_reference)) WHERE status IN('PENDING','APPROVED','SETTLED');
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_operating_settlement ON finance_operating_entries(settlement_method,lower(settlement_reference)) WHERE settlement_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_finance_operating_queue ON finance_operating_entries(status,document_date DESC,id);
CREATE INDEX IF NOT EXISTS idx_finance_operating_date ON finance_operating_entries(document_date DESC,id);
