-- Run with psql in autocommit mode, not inside BEGIN/COMMIT.
-- CONCURRENTLY avoids blocking normal fee writes during index creation.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fees_recent_finance
    ON fees (created_at DESC, id DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fees_upcoming_finance
    ON fees (due_date, id)
    WHERE status <> 'WAIVED' AND amount > amount_paid;
