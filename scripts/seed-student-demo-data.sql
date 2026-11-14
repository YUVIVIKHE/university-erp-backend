-- Local-only, repeat-safe demo data for student@fairbazaar.in.
-- Apply manually after the numbered schema migrations; this does not change login credentials.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM users u
        JOIN students s ON s.user_id = u.id
        WHERE lower(u.email) = lower('student@fairbazaar.in')
          AND u.role = 'STUDENT'
          AND s.is_active
    ) THEN
        RAISE EXCEPTION 'Active student account student@fairbazaar.in was not found';
    END IF;
END
$$;

INSERT INTO admissions (
    id, admission_id, first_name, last_name, email, date_of_birth, gender, address,
    city, state, pincode, nationality, program, semester, batch_year, prev_school,
    prev_board, prev_year, prev_percentage, guardian_name, guardian_relation,
    guardian_phone, guardian_email, status, created_at, updated_at
)
SELECT COALESCE((SELECT MAX(id) FROM admissions), 0) + 1,
       'DEMO-FB-2025-001', 'Fair Bazaar', 'Student', u.email, s.date_of_birth,
       'UNSPECIFIED', s.address, 'Mumbai', 'Maharashtra', '400001', 'Indian',
       s.program, s.semester, s.batch_year, 'Fair Bazaar Senior Secondary School',
       'State Board', '2024', '82%', s.guardian_name, 'Parent',
       s.guardian_phone, NULL, 'APPROVED', NOW(), NOW()
FROM users u
JOIN students s ON s.user_id = u.id
WHERE lower(u.email) = lower('student@fairbazaar.in')
  AND NOT EXISTS (
      SELECT 1 FROM admissions WHERE admission_id = 'DEMO-FB-2025-001'
  );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM admissions
        WHERE admission_id = 'DEMO-FB-2025-001'
          AND lower(email) = lower('student@fairbazaar.in')
          AND status = 'APPROVED'
    ) THEN
        RAISE EXCEPTION 'Expected approved demo admission is missing or belongs to another account';
    END IF;
END
$$;

INSERT INTO scholarship_offers (
    scheme, description, category, funding, program, min_semester, max_semester,
    min_batch_year, max_batch_year, max_amount, academic_year, application_deadline,
    eligibility_criteria, is_active
)
VALUES
    ('Continuing Student Tuition Scholarship',
     'Demo scholarship for active undergraduate students continuing their studies.',
     'SCHOLARSHIP', 'INSTITUTION', NULL, 1, 4, 2024, 2028, 20000.00,
     EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER, CURRENT_DATE + 60,
     'Active student profile; semesters 1-4; batch years 2024-2028. Program, semester, and batch year are checked against the student record.',
     TRUE),
    ('Campus Access Concession',
     'Demo fee concession for active students in eligible undergraduate terms.',
     'CONCESSION', 'INSTITUTION', NULL, 1, 3, 2024, 2028, 5000.00,
     EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER, CURRENT_DATE + 45,
     'Active student profile; semesters 1-3; batch years 2024-2028. Program, semester, and batch year are checked against the student record.',
     TRUE),
    ('Advanced Semester Research Grant',
     'Demo grant for students in advanced semesters undertaking research work.',
     'SCHOLARSHIP', 'EXTERNAL', NULL, 5, 8, 2022, 2026, 25000.00,
     EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER, CURRENT_DATE + 90,
     'Active student profile; semesters 5-8; batch years 2022-2026. Program, semester, and batch year are checked against the student record.',
     TRUE)
ON CONFLICT (scheme) DO NOTHING;

INSERT INTO subjects (
    subject_code, name, description, course, semester, year, credits, type,
    faculty_name, status, created_at, updated_at
)
SELECT d.subject_code, d.name, d.description, s.program, s.semester, s.batch_year,
       d.credits, d.type, d.faculty_name, 'ACTIVE', NOW(), NOW()
FROM (
    VALUES
        ('DEMO-BBA-101', 'Business Communication',
         'Written, oral, and professional communication for business contexts.',
         3, 'THEORY', 'Dr. Ananya Shah'),
        ('DEMO-BBA-102', 'Principles of Management',
         'Foundations of planning, organizing, leadership, and control.',
         4, 'THEORY', 'Prof. Rohan Mehta'),
        ('DEMO-BBA-103', 'Financial Accounting I',
         'Accounting cycle, journal entries, ledgers, and financial statements.',
         4, 'THEORY', 'Ms. Isha Desai'),
        ('DEMO-BBA-104', 'Business Economics',
         'Microeconomic concepts and their application to business decisions.',
         3, 'THEORY', 'Dr. Kabir Rao'),
        ('DEMO-BBA-105', 'Business Computing Lab',
         'Practical spreadsheet and business-data skills.',
         2, 'PRACTICAL', 'Ms. Neha Kulkarni')
) AS d(subject_code, name, description, credits, type, faculty_name)
CROSS JOIN students s
JOIN users u ON u.id = s.user_id
WHERE lower(u.email) = lower('student@fairbazaar.in')
  AND NOT EXISTS (
      SELECT 1 FROM subjects existing WHERE existing.subject_code = d.subject_code
  )
ON CONFLICT (subject_code) DO NOTHING;

INSERT INTO timetables (
    program, course, batch_year, semester, academic_year, status, created_at, updated_at
)
SELECT s.program, s.program, s.batch_year, s.semester, '2025-26', 'PUBLISHED', NOW(), NOW()
FROM students s
JOIN users u ON u.id = s.user_id
WHERE lower(u.email) = lower('student@fairbazaar.in')
ON CONFLICT (program, course, batch_year, semester) DO NOTHING;

WITH demo_slots(day, start_time, end_time, type, subject_code, faculty_name, room, slot_order) AS (
    VALUES
        ('MON', TIME '09:00', TIME '10:00', 'CLASS', 'DEMO-BBA-101', 'Dr. Ananya Shah', 'B-201', 1),
        ('MON', TIME '10:00', TIME '11:00', 'CLASS', 'DEMO-BBA-102', 'Prof. Rohan Mehta', 'B-201', 2),
        ('MON', TIME '11:00', TIME '11:15', 'BREAK', NULL, NULL, NULL, 3),
        ('MON', TIME '11:15', TIME '13:15', 'LAB', 'DEMO-BBA-105', 'Ms. Neha Kulkarni', 'Computer Lab 2', 4),
        ('TUE', TIME '09:00', TIME '10:00', 'CLASS', 'DEMO-BBA-103', 'Ms. Isha Desai', 'B-202', 1),
        ('TUE', TIME '10:00', TIME '11:00', 'CLASS', 'DEMO-BBA-104', 'Dr. Kabir Rao', 'B-202', 2),
        ('WED', TIME '09:00', TIME '10:00', 'CLASS', 'DEMO-BBA-101', 'Dr. Ananya Shah', 'B-201', 1),
        ('WED', TIME '10:00', TIME '11:00', 'CLASS', 'DEMO-BBA-102', 'Prof. Rohan Mehta', 'B-201', 2),
        ('THU', TIME '09:00', TIME '10:00', 'CLASS', 'DEMO-BBA-103', 'Ms. Isha Desai', 'B-202', 1),
        ('THU', TIME '10:00', TIME '11:00', 'CLASS', 'DEMO-BBA-104', 'Dr. Kabir Rao', 'B-202', 2)
)
INSERT INTO timetable_slots (
    timetable_id, day, start_time, end_time, type, subject_code,
    subject_name, faculty_name, room, slot_order
)
SELECT t.id, d.day, d.start_time, d.end_time, d.type, d.subject_code,
       sub.name, d.faculty_name, d.room, d.slot_order
FROM timetables t
JOIN students s ON s.program = t.program AND s.batch_year = t.batch_year
JOIN users u ON u.id = s.user_id
CROSS JOIN demo_slots d
LEFT JOIN subjects sub ON sub.subject_code = d.subject_code
WHERE lower(u.email) = lower('student@fairbazaar.in')
  AND t.course = s.program
  AND t.semester = s.semester
  AND (d.subject_code IS NULL OR sub.id IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1
      FROM timetable_slots existing
      WHERE existing.timetable_id = t.id
        AND existing.day = d.day
        AND existing.start_time = d.start_time
        AND existing.end_time = d.end_time
        AND existing.type = d.type
        AND existing.subject_code IS NOT DISTINCT FROM d.subject_code
  );

WITH class_patterns(subject_code, day_offset, time_slot) AS (
    VALUES
        ('DEMO-BBA-101', 0, '09:00-10:00'),
        ('DEMO-BBA-101', 2, '09:00-10:00'),
        ('DEMO-BBA-102', 0, '10:00-11:00'),
        ('DEMO-BBA-102', 2, '10:00-11:00'),
        ('DEMO-BBA-103', 1, '09:00-10:00'),
        ('DEMO-BBA-103', 3, '09:00-10:00'),
        ('DEMO-BBA-104', 1, '10:00-11:00'),
        ('DEMO-BBA-104', 3, '10:00-11:00'),
        ('DEMO-BBA-105', 0, '11:15-13:15')
),
student_demo AS (
    SELECT s.id, s.program, s.semester
    FROM students s
    JOIN users u ON u.id = s.user_id
    WHERE lower(u.email) = lower('student@fairbazaar.in')
),
sessions AS (
    SELECT d.id AS student_id, p.subject_code,
           DATE '2025-10-06' + week.week_index * 7 + p.day_offset AS session_date,
           p.time_slot,
           CASE
               WHEN p.subject_code = 'DEMO-BBA-101' AND DATE '2025-10-06' + week.week_index * 7 + p.day_offset = DATE '2025-10-15' THEN 'ABSENT'
               WHEN p.subject_code = 'DEMO-BBA-102' AND DATE '2025-10-06' + week.week_index * 7 + p.day_offset = DATE '2025-10-20' THEN 'ABSENT'
               WHEN p.subject_code = 'DEMO-BBA-103' AND DATE '2025-10-06' + week.week_index * 7 + p.day_offset = DATE '2025-10-23' THEN 'ABSENT'
               WHEN p.subject_code = 'DEMO-BBA-104' AND DATE '2025-10-06' + week.week_index * 7 + p.day_offset IN (DATE '2025-10-21', DATE '2025-10-30') THEN 'ABSENT'
               WHEN p.subject_code = 'DEMO-BBA-105' AND DATE '2025-10-06' + week.week_index * 7 + p.day_offset = DATE '2025-10-20' THEN 'ABSENT'
               ELSE 'PRESENT'
           END AS status
    FROM student_demo d
    CROSS JOIN generate_series(0, 3) AS week(week_index)
    CROSS JOIN class_patterns p
)
INSERT INTO attendance (
    student_id, subject_code, subject_name, date, time_slot, status, remarks,
    marked_by, created_at, updated_at
)
SELECT session.student_id, sub.subject_code, sub.name, session.session_date,
       session.time_slot, session.status,
       CASE WHEN session.status = 'ABSENT' THEN 'Demo absence record'
            ELSE 'Demo attendance record' END,
       'Demo academic seed', NOW(), NOW()
FROM sessions session
JOIN student_demo d ON d.id = session.student_id
JOIN subjects sub ON sub.subject_code = session.subject_code
                 AND sub.course = d.program
                 AND sub.semester = d.semester
WHERE NOT EXISTS (
    SELECT 1 FROM attendance existing
    WHERE existing.student_id = session.student_id
      AND existing.subject_code = session.subject_code
      AND existing.date = session.session_date
      AND existing.time_slot = session.time_slot
)
ON CONFLICT (student_id, subject_code, date, time_slot) DO NOTHING;

INSERT INTO exam_schedules (
    exam_code, title, exam_type, program, course, semester, academic_year,
    exam_date, start_time, end_time, venue, total_marks, passing_marks,
    subject_id, status, instructions, created_at, updated_at
)
SELECT d.exam_code, d.title, d.exam_type, s.program, s.program, s.semester,
       '2025-26', d.exam_date, TIME '09:00', TIME '12:00', d.venue,
       100.00, 40.00, sub.id, 'COMPLETED',
       'Demo final examination record for student portal testing.', NOW(), NOW()
FROM (
    VALUES
        ('DEMO-BBA-2025-FIN-101', 'Semester I Final - Business Communication', 'FINAL', '2025-12-09'::date, 'Main Hall 1'),
        ('DEMO-BBA-2025-FIN-102', 'Semester I Final - Principles of Management', 'FINAL', '2025-12-10'::date, 'Main Hall 1'),
        ('DEMO-BBA-2025-FIN-103', 'Semester I Final - Financial Accounting I', 'FINAL', '2025-12-11'::date, 'Main Hall 2'),
        ('DEMO-BBA-2025-FIN-104', 'Semester I Final - Business Economics', 'FINAL', '2025-12-12'::date, 'Main Hall 2'),
        ('DEMO-BBA-2025-FIN-105', 'Semester I Practical - Business Computing Lab', 'PRACTICAL', '2025-12-15'::date, 'Computer Lab 2')
) AS d(exam_code, title, exam_type, exam_date, venue)
CROSS JOIN students s
JOIN users u ON u.id = s.user_id
JOIN subjects sub ON sub.subject_code = 'DEMO-BBA-' || RIGHT(d.exam_code, 3)
                 AND sub.course = s.program
                 AND sub.semester = s.semester
WHERE lower(u.email) = lower('student@fairbazaar.in')
  AND NOT EXISTS (
      SELECT 1 FROM exam_schedules existing WHERE existing.exam_code = d.exam_code
  )
ON CONFLICT (exam_code) DO NOTHING;

INSERT INTO exam_results (
    exam_id, student_id, marks_obtained, grade, grade_points, is_absent,
    is_pass, remarks, entered_at, created_at, updated_at
)
SELECT exam.id, s.id, d.marks_obtained, d.grade, d.grade_points, FALSE, TRUE,
       'Demo result for student portal testing.', NOW(), NOW(), NOW()
FROM (
    VALUES
        ('DEMO-BBA-2025-FIN-101', 86.00::numeric, 'A+', 9.00::numeric),
        ('DEMO-BBA-2025-FIN-102', 78.00::numeric, 'A', 8.00::numeric),
        ('DEMO-BBA-2025-FIN-103', 67.00::numeric, 'B+', 7.00::numeric),
        ('DEMO-BBA-2025-FIN-104', 74.00::numeric, 'A', 8.00::numeric),
        ('DEMO-BBA-2025-FIN-105', 92.00::numeric, 'O', 10.00::numeric)
) AS d(exam_code, marks_obtained, grade, grade_points)
JOIN exam_schedules exam ON exam.exam_code = d.exam_code
CROSS JOIN students s
JOIN users u ON u.id = s.user_id
WHERE lower(u.email) = lower('student@fairbazaar.in')
  AND NOT EXISTS (
      SELECT 1 FROM exam_results existing
      WHERE existing.exam_id = exam.id AND existing.student_id = s.id
  )
ON CONFLICT (exam_id, student_id) DO NOTHING;

INSERT INTO library_books (
    accession_no, isbn, title, author, publisher, category, shelf, total_copies, available_copies
)
VALUES
    ('DEMO-FB-BOOK-001', '9780262046305', 'Introduction to Algorithms', 'Thomas H. Cormen et al.',
     'MIT Press', 'Computer Science', 'CS-A1', 2, 2),
    ('DEMO-FB-BOOK-002', '9780131103627', 'The C Programming Language', 'Brian W. Kernighan and Dennis M. Ritchie',
     'Prentice Hall', 'Programming', 'CS-B2', 2, 2),
    ('DEMO-FB-BOOK-003', '9780073523323', 'Computer Networks: A Systems Approach', 'Larry Peterson and Bruce Davie',
     'Morgan Kaufmann', 'Networking', 'CS-C1', 2, 2)
ON CONFLICT (accession_no) DO NOTHING;

INSERT INTO library_issues (book_id, student_id, issue_date, due_date, status, remarks)
SELECT b.id, s.id, CURRENT_DATE - 21, CURRENT_DATE - 7, 'ISSUED', 'Demo loan for student portal testing'
FROM library_books b
CROSS JOIN students s
JOIN users u ON u.id = s.user_id
WHERE b.accession_no = 'DEMO-FB-BOOK-003'
  AND lower(u.email) = lower('student@fairbazaar.in')
  AND NOT EXISTS (
      SELECT 1 FROM library_issues li
      WHERE li.book_id = b.id AND li.student_id = s.id AND li.status = 'ISSUED'
  );

INSERT INTO library_issues (book_id, student_id, issue_date, due_date, status, remarks)
SELECT b.id, s.id, CURRENT_DATE - 7, CURRENT_DATE + 7, 'ISSUED', 'Demo loan for student portal testing'
FROM library_books b
CROSS JOIN students s
JOIN users u ON u.id = s.user_id
WHERE b.accession_no = 'DEMO-FB-BOOK-001'
  AND lower(u.email) = lower('student@fairbazaar.in')
  AND NOT EXISTS (
      SELECT 1 FROM library_issues li
      WHERE li.book_id = b.id AND li.student_id = s.id AND li.status = 'ISSUED'
  );

UPDATE library_books b
SET available_copies = b.total_copies - (
    SELECT COUNT(*)::INTEGER
    FROM library_issues li
    WHERE li.book_id = b.id AND li.status = 'ISSUED'
), updated_at = NOW()
WHERE b.accession_no IN ('DEMO-FB-BOOK-001', 'DEMO-FB-BOOK-002', 'DEMO-FB-BOOK-003');

INSERT INTO fees (
    id, fee_id, admission_id, fee_type, amount, amount_paid, concession_amount,
    due_date, status, remarks, created_at, updated_at
)
SELECT COALESCE((SELECT MAX(id) FROM fees), 0) + 1,
       'DEMO-FB-TUITION-2026', 'DEMO-FB-2025-001', 'Tuition Fee',
       100000.00, 0.00, 0.00, CURRENT_DATE + 30, 'PENDING',
       'Demo tuition charge for student scholarship award testing', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM fees WHERE fee_id = 'DEMO-FB-TUITION-2026');

INSERT INTO finance_scholarship_applications (
    id, request_id, admission_id, scheme, category, funding, academic_year,
    requested_amount, evidence_reference, reason, status, created_by,
    created_at, reviewed_by, reviewed_at, review_note
)
VALUES
    ('a126f400-0bd3-4f66-9e7d-000000000001', 'b126f400-0bd3-4f66-9e7d-000000000001',
     'DEMO-FB-2025-001', 'University Need-Based Scholarship', 'SCHOLARSHIP', 'INSTITUTION',
     EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER, 20000.00, 'DEMO: household income statement',
     'Support toward tuition based on the submitted household income statement.',
     'VERIFIED', 'student@fairbazaar.in', NOW() - INTERVAL '14 days', 'demo-finance-reviewer',
     NOW() - INTERVAL '7 days', 'Documents verified; demo award approved and posted against tuition charge.'),
    ('a126f400-0bd3-4f66-9e7d-000000000002', 'b126f400-0bd3-4f66-9e7d-000000000002',
     'DEMO-FB-2025-001', 'Merit and Access Grant', 'SCHOLARSHIP', 'EXTERNAL',
     EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER, 12000.00, 'DEMO: academic transcript',
     'Request for assistance with books and academic supplies.',
     'SUBMITTED', 'student@fairbazaar.in', NOW() - INTERVAL '2 days', NULL, NULL, NULL),
    ('a126f400-0bd3-4f66-9e7d-000000000003', 'b126f400-0bd3-4f66-9e7d-000000000003',
     'DEMO-FB-2025-001', 'Campus Access Concession', 'CONCESSION', 'INSTITUTION',
     EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER, 5000.00, 'DEMO: supporting document',
     'Request for a fee concession under the campus access support scheme.',
     'REJECTED', 'student@fairbazaar.in', NOW() - INTERVAL '30 days', 'demo-finance-reviewer',
     NOW() - INTERVAL '20 days', 'Demo decision: the submitted evidence did not satisfy this scheme criterion.')
ON CONFLICT (id) DO NOTHING;

UPDATE finance_scholarship_applications
SET review_note = 'Documents verified; demo award approved and posted against tuition charge.'
WHERE id = 'a126f400-0bd3-4f66-9e7d-000000000001'
  AND created_by = 'student@fairbazaar.in'
  AND review_note IN (
      'Documents verified; awaiting financial award decision.',
      'Documents verified; demo award approved and posted against tuition charge.'
  );

INSERT INTO finance_award_decisions (
    id, application_id, request_id, request_hash, outcome, amount, note, decided_by, created_at
)
SELECT 'c126f400-0bd3-4f66-9e7d-000000000001',
       'a126f400-0bd3-4f66-9e7d-000000000001',
       'd126f400-0bd3-4f66-9e7d-000000000001',
       repeat('a', 64), 'POSTED', 15000.00,
       'Demo award posted against the student tuition charge.',
       'demo-finance-approver', NOW() - INTERVAL '6 days'
WHERE NOT EXISTS (
    SELECT 1 FROM finance_award_decisions
    WHERE application_id = 'a126f400-0bd3-4f66-9e7d-000000000001'
);

INSERT INTO finance_concession_allocations (
    decision_id, fee_id, fee_type, amount, balance_before, balance_after
)
SELECT 'c126f400-0bd3-4f66-9e7d-000000000001',
       'DEMO-FB-TUITION-2026', 'Tuition Fee', 15000.00, 100000.00, 85000.00
WHERE EXISTS (
    SELECT 1 FROM finance_award_decisions
    WHERE id = 'c126f400-0bd3-4f66-9e7d-000000000001'
      AND application_id = 'a126f400-0bd3-4f66-9e7d-000000000001'
      AND outcome = 'POSTED'
)
ON CONFLICT (decision_id, fee_id) DO NOTHING;

UPDATE fees
SET concession_amount = GREATEST(concession_amount, 15000.00),
    updated_at = NOW()
WHERE fee_id = 'DEMO-FB-TUITION-2026'
  AND EXISTS (
      SELECT 1 FROM finance_award_decisions
      WHERE id = 'c126f400-0bd3-4f66-9e7d-000000000001'
        AND application_id = 'a126f400-0bd3-4f66-9e7d-000000000001'
        AND outcome = 'POSTED'
  );

COMMIT;
