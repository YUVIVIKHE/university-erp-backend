-- Apply once to an existing database before creating Accountant users.
-- This expands the standard users role constraint without changing any records.
BEGIN;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('STUDENT', 'FACULTY', 'ADMIN', 'ACCOUNTANT'));
COMMIT;
