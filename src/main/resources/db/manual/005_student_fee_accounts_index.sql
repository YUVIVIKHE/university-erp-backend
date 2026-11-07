-- psql autocommit required. Supports scoped account totals and charge lists.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fees_admission_due_finance
  ON fees (admission_id, due_date, id);
