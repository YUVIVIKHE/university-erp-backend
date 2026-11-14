CREATE TABLE IF NOT EXISTS finance_vendors (
 id UUID PRIMARY KEY, request_id UUID UNIQUE NOT NULL, request_hash VARCHAR(64) NOT NULL,
 name VARCHAR(150) NOT NULL, email VARCHAR(255), active BOOLEAN NOT NULL DEFAULT true,
 created_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_vendor_name ON finance_vendors(lower(name));
ALTER TABLE finance_operating_entries ADD COLUMN IF NOT EXISTS vendor_id UUID REFERENCES finance_vendors(id);
ALTER TABLE finance_operating_entries ADD COLUMN IF NOT EXISTS staff_user_id BIGINT REFERENCES users(id);
ALTER TABLE finance_operating_entries ADD COLUMN IF NOT EXISTS payroll_month DATE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_payroll_month ON finance_operating_entries(staff_user_id,payroll_month) WHERE staff_user_id IS NOT NULL AND status IN('PENDING','APPROVED','SETTLED');
CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_vendor_invoice ON finance_operating_entries(vendor_id,lower(document_reference)) WHERE vendor_id IS NOT NULL AND status IN('PENDING','APPROVED','SETTLED');
CREATE INDEX IF NOT EXISTS idx_finance_vendor_payments ON finance_operating_entries(vendor_id,status,document_date DESC);
INSERT INTO finance_ledger_accounts(code,name,kind) VALUES('5210','Vendor payments','EXPENSE'),('5200','Staff net payouts','EXPENSE') ON CONFLICT(code) DO NOTHING;
CREATE OR REPLACE VIEW finance_ledger_entries AS
 SELECT 'RECEIPT-'||id::text AS entry_id,'RECEIPT'::text AS source_type,id AS source_id,
   paid_date AS entry_date,receipt_number::text AS reference,student_name::text AS counterparty,
   'Student fee receipt'::text AS description,amount,
   CASE WHEN method='CASH' THEN '1000' ELSE '1010' END::varchar(10) AS debit_account,
   '4000'::varchar(10) AS credit_account
 FROM finance_fee_payments WHERE receipt_number IS NOT NULL
 UNION ALL
 SELECT 'REFUND-'||r.id::text,'REFUND',r.id,r.payout_date,r.payout_reference::text,p.student_name::text,
   'Fee refund: '||p.receipt_number,r.amount,'4090',CASE WHEN r.payout_method='CASH' THEN '1000' ELSE '1010' END
 FROM finance_refunds r JOIN finance_fee_payments p ON p.id=r.payment_id WHERE r.status='PAID'
 UNION ALL
 SELECT CASE WHEN vendor_id IS NOT NULL THEN 'VENDOR-' WHEN staff_user_id IS NOT NULL THEN 'PAYROLL-' ELSE 'OPERATING-' END||id::text,CASE WHEN vendor_id IS NOT NULL THEN 'VENDOR' WHEN staff_user_id IS NOT NULL THEN 'PAYROLL' ELSE 'OPERATING' END,id,settlement_date,settlement_reference::text,counterparty::text,
   category||': '||document_reference,amount,
   CASE WHEN direction='INCOME' THEN CASE WHEN settlement_method='CASH' THEN '1000' ELSE '1010' END
     ELSE CASE category WHEN 'UTILITIES' THEN '5100' WHEN 'MAINTENANCE' THEN '5110' WHEN 'ADMINISTRATION' THEN '5120' WHEN 'EVENT' THEN '5130' WHEN 'VENDOR_PAYMENT' THEN '5210' WHEN 'PAYROLL' THEN '5200' ELSE '5190' END END,
   CASE WHEN direction='EXPENSE' THEN CASE WHEN settlement_method='CASH' THEN '1000' ELSE '1010' END
     ELSE CASE category WHEN 'DONATION' THEN '4100' WHEN 'GRANT' THEN '4110' WHEN 'EVENT' THEN '4120' WHEN 'INTEREST' THEN '4130' ELSE '4190' END END
 FROM finance_operating_entries WHERE status='SETTLED';

