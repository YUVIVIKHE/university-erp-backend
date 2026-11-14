-- Read-only cash-basis projection of immutable, verified source transactions.
CREATE TABLE IF NOT EXISTS finance_ledger_accounts (
 code VARCHAR(10) PRIMARY KEY, name VARCHAR(100) NOT NULL,
 kind VARCHAR(10) NOT NULL CHECK(kind IN('ASSET','INCOME','EXPENSE'))
);
INSERT INTO finance_ledger_accounts(code,name,kind) VALUES
 ('1000','Cash','ASSET'),('1010','Bank / digital clearing','ASSET'),
 ('4000','Student fee receipts','INCOME'),('4090','Fee refunds (contra income)','INCOME'),
 ('4100','Donations','INCOME'),('4110','Non-fee grants','INCOME'),('4120','Event income','INCOME'),
 ('4130','Interest income','INCOME'),('4190','Other non-fee income','INCOME'),
 ('5100','Utilities','EXPENSE'),('5110','Maintenance','EXPENSE'),('5120','Administration','EXPENSE'),
 ('5130','Event expenses','EXPENSE'),('5190','Other operating expenses','EXPENSE')
 ON CONFLICT(code) DO NOTHING;

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
 SELECT 'OPERATING-'||id::text,'OPERATING',id,settlement_date,settlement_reference::text,counterparty::text,
   category||': '||document_reference,amount,
   CASE WHEN direction='INCOME' THEN CASE WHEN settlement_method='CASH' THEN '1000' ELSE '1010' END
     ELSE CASE category WHEN 'UTILITIES' THEN '5100' WHEN 'MAINTENANCE' THEN '5110' WHEN 'ADMINISTRATION' THEN '5120' WHEN 'EVENT' THEN '5130' ELSE '5190' END END,
   CASE WHEN direction='EXPENSE' THEN CASE WHEN settlement_method='CASH' THEN '1000' ELSE '1010' END
     ELSE CASE category WHEN 'DONATION' THEN '4100' WHEN 'GRANT' THEN '4110' WHEN 'EVENT' THEN '4120' WHEN 'INTEREST' THEN '4130' ELSE '4190' END END
 FROM finance_operating_entries WHERE status='SETTLED';

CREATE OR REPLACE VIEW finance_ledger_lines AS
 SELECT entry_id,source_type,source_id,entry_date,reference,counterparty,description,debit_account AS account_code,amount AS debit,0::numeric AS credit FROM finance_ledger_entries
 UNION ALL
 SELECT entry_id,source_type,source_id,entry_date,reference,counterparty,description,credit_account,0::numeric,amount FROM finance_ledger_entries;
