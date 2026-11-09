-- ============================================================
-- Faculty Management + Exam/Results
-- Migration 021
-- ============================================================

-- Faculty profiles (one-to-one with users)
CREATE TABLE IF NOT EXISTS faculty (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    employee_id     VARCHAR(30) NOT NULL UNIQUE,
    department      VARCHAR(120) NOT NULL,
    designation     VARCHAR(100) NOT NULL DEFAULT 'Assistant Professor',
    specialization  VARCHAR(255),
    qualification   VARCHAR(255),
    date_of_joining DATE,
    date_of_birth   DATE,
    phone           VARCHAR(20),
    address         TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_faculty_user       ON faculty(user_id);
CREATE INDEX IF NOT EXISTS idx_faculty_dept       ON faculty(department);
CREATE INDEX IF NOT EXISTS idx_faculty_emp_id     ON faculty(employee_id);

-- Faculty–Subject assignments (many-to-many)
CREATE TABLE IF NOT EXISTS faculty_subject_assignments (
    id              BIGSERIAL PRIMARY KEY,
    faculty_id      BIGINT NOT NULL REFERENCES faculty(id) ON DELETE CASCADE,
    subject_id      BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    academic_year   VARCHAR(20) NOT NULL,  -- e.g. "2024-25"
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (faculty_id, subject_id, academic_year)
);

CREATE INDEX IF NOT EXISTS idx_fsa_faculty  ON faculty_subject_assignments(faculty_id);
CREATE INDEX IF NOT EXISTS idx_fsa_subject  ON faculty_subject_assignments(subject_id);

-- Exam schedules
CREATE TABLE IF NOT EXISTS exam_schedules (
    id              BIGSERIAL PRIMARY KEY,
    exam_code       VARCHAR(30) NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    exam_type       VARCHAR(30) NOT NULL CHECK (exam_type IN ('MIDTERM','FINAL','INTERNAL','PRACTICAL','VIVA','ASSIGNMENT')),
    program         VARCHAR(100) NOT NULL,
    course          VARCHAR(100) NOT NULL,
    semester        SMALLINT NOT NULL CHECK (semester BETWEEN 1 AND 12),
    academic_year   VARCHAR(20) NOT NULL,
    exam_date       DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    venue           VARCHAR(200),
    total_marks     NUMERIC(6,2) NOT NULL DEFAULT 100,
    passing_marks   NUMERIC(6,2) NOT NULL DEFAULT 40,
    subject_id      BIGINT REFERENCES subjects(id) ON DELETE SET NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                        CHECK (status IN ('SCHEDULED','ONGOING','COMPLETED','CANCELLED')),
    instructions    TEXT,
    created_by      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_exam_program  ON exam_schedules(program);
CREATE INDEX IF NOT EXISTS idx_exam_semester ON exam_schedules(semester);
CREATE INDEX IF NOT EXISTS idx_exam_date     ON exam_schedules(exam_date);
CREATE INDEX IF NOT EXISTS idx_exam_status   ON exam_schedules(status);

-- Exam results (one row per student per exam)
CREATE TABLE IF NOT EXISTS exam_results (
    id              BIGSERIAL PRIMARY KEY,
    exam_id         BIGINT NOT NULL REFERENCES exam_schedules(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    marks_obtained  NUMERIC(6,2),
    grade           VARCHAR(5),
    grade_points    NUMERIC(4,2),
    is_absent       BOOLEAN NOT NULL DEFAULT FALSE,
    is_pass         BOOLEAN,
    remarks         TEXT,
    entered_by      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    entered_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (exam_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_result_exam    ON exam_results(exam_id);
CREATE INDEX IF NOT EXISTS idx_result_student ON exam_results(student_id);

-- Grant permissions
INSERT INTO permissions (name, description) VALUES
    ('MANAGE_FACULTY',   'Create and update faculty records'),
    ('VIEW_FACULTY',     'View faculty records'),
    ('MANAGE_EXAMS',     'Create and manage exam schedules'),
    ('ENTER_RESULTS',    'Enter and update exam results'),
    ('VIEW_RESULTS',     'View exam results')
ON CONFLICT (name) DO NOTHING;
