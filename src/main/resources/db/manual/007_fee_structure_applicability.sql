-- Optional metadata; preserves all existing templates and their lines.
ALTER TABLE finance_fee_structures ADD COLUMN IF NOT EXISTS batch_year INTEGER;
ALTER TABLE finance_fee_structures ADD COLUMN IF NOT EXISTS effective_from DATE;
ALTER TABLE finance_fee_structures ADD COLUMN IF NOT EXISTS effective_until DATE;
ALTER TABLE finance_fee_structures ADD COLUMN IF NOT EXISTS notes VARCHAR(1000);
