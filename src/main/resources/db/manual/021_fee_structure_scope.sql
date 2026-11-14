-- Preserve existing single-course plans and assignment runs as LEGACY.
ALTER TABLE finance_fee_structures ALTER COLUMN course_id DROP NOT NULL;
ALTER TABLE finance_fee_structures ADD COLUMN IF NOT EXISTS course_scope VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE finance_fee_structures ADD COLUMN IF NOT EXISTS include_final_year BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE finance_fee_structures DROP CONSTRAINT IF EXISTS finance_fee_structures_semester_check;
ALTER TABLE finance_fee_structures ADD CONSTRAINT finance_fee_structures_semester_check CHECK(semester BETWEEN 0 AND 100);
CREATE TABLE IF NOT EXISTS finance_fee_structure_courses (
 structure_id BIGINT NOT NULL REFERENCES finance_fee_structures(id),
 course_id BIGINT NOT NULL REFERENCES courses(id),PRIMARY KEY(structure_id,course_id)
);
CREATE TABLE IF NOT EXISTS finance_fee_structure_years (
 structure_id BIGINT NOT NULL REFERENCES finance_fee_structures(id),
 study_year INTEGER NOT NULL CHECK(study_year BETWEEN 1 AND 50),PRIMARY KEY(structure_id,study_year)
);
CREATE INDEX IF NOT EXISTS idx_finance_scope_course ON finance_fee_structure_courses(course_id,structure_id);
ALTER TABLE finance_fee_assignment_runs ADD COLUMN IF NOT EXISTS mapping_unresolved BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_finance_course_name_match ON courses(lower(btrim(name))) WHERE status='ACTIVE';
CREATE INDEX IF NOT EXISTS idx_finance_course_program_match ON courses(lower(btrim(program))) WHERE status='ACTIVE';
