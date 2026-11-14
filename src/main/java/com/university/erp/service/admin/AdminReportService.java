package com.university.erp.service.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Tabular reports for preview + export. Columns are the human-readable SQL aliases. */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    public static final int MAX_ROWS = 10000;
    private final NamedParameterJdbcTemplate jdbc;

    public record Filters(String program, Integer semester, String status, LocalDate from, LocalDate to, Long examId) {}

    public record Report(String type, String title, List<String> columns, List<List<Object>> rows, boolean truncated) {}

    @Transactional(readOnly = true)
    public Report run(String type, Filters f) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate from = f.from() != null ? f.from() : today.minusDays(29);
        LocalDate to = f.to() != null ? f.to() : today;
        if (from.isAfter(to)) throw new IllegalArgumentException("'From' date must be before 'To' date");

        var p = new MapSqlParameterSource("program", f.program() == null ? "" : f.program())
            .addValue("semester", f.semester(), java.sql.Types.INTEGER)
            .addValue("status", f.status() == null ? "" : f.status().toUpperCase())
            .addValue("from", from).addValue("to", to).addValue("today", today)
            .addValue("exam", f.examId(), java.sql.Types.BIGINT)
            .addValue("limit", MAX_ROWS + 1);

        String title;
        String sql;
        switch (type) {
            case "students" -> {
                title = "Student List";
                sql = """
                    SELECT s.roll_number AS "Roll No", u.full_name AS "Name", u.email AS "Email", s.program AS "Program",
                           s.semester AS "Semester", s.batch_year AS "Batch", s.phone AS "Phone",
                           s.guardian_name AS "Guardian", s.guardian_phone AS "Guardian Phone",
                           s.enrollment_date AS "Enrolled On", CASE WHEN s.is_active THEN 'Active' ELSE 'Inactive' END AS "Status"
                    FROM students s JOIN users u ON u.id = s.user_id
                    WHERE (:program = '' OR s.program = :program) AND (:semester IS NULL OR s.semester = :semester)
                      AND (:status = '' OR (:status = 'ACTIVE' AND s.is_active) OR (:status = 'INACTIVE' AND NOT s.is_active))
                    ORDER BY s.program, s.semester, s.roll_number LIMIT :limit
                    """;
            }
            case "attendance" -> {
                title = "Attendance Summary (" + from + " to " + to + ")";
                sql = """
                    SELECT s.roll_number AS "Roll No", u.full_name AS "Name", s.program AS "Program", s.semester AS "Semester",
                           COUNT(a.id) AS "Classes",
                           COUNT(a.id) FILTER (WHERE a.status = 'PRESENT') AS "Present",
                           COUNT(a.id) FILTER (WHERE a.status = 'LATE') AS "Late",
                           COUNT(a.id) FILTER (WHERE a.status = 'ABSENT') AS "Absent",
                           COUNT(a.id) FILTER (WHERE a.status = 'EXCUSED') AS "Excused",
                           ROUND(COUNT(a.id) FILTER (WHERE a.status IN ('PRESENT','LATE')) * 100.0 / NULLIF(COUNT(a.id), 0), 1) AS "Attendance %"
                    FROM students s JOIN users u ON u.id = s.user_id
                    LEFT JOIN attendance a ON a.student_id = s.id AND a.date BETWEEN :from AND :to
                    WHERE s.is_active AND (:program = '' OR s.program = :program) AND (:semester IS NULL OR s.semester = :semester)
                    GROUP BY s.id, u.full_name ORDER BY s.program, s.semester, s.roll_number LIMIT :limit
                    """;
            }
            case "results" -> {
                title = "Exam Results";
                sql = """
                    SELECT e.exam_code AS "Exam Code", e.title AS "Exam", e.exam_date AS "Date", s.roll_number AS "Roll No",
                           u.full_name AS "Name", e.program AS "Program", e.semester AS "Semester",
                           CASE WHEN r.is_absent THEN NULL ELSE r.marks_obtained END AS "Marks", e.total_marks AS "Out Of",
                           CASE WHEN r.is_absent THEN NULL ELSE ROUND(r.marks_obtained * 100.0 / NULLIF(e.total_marks, 0), 1) END AS "Percentage",
                           r.grade AS "Grade",
                           CASE WHEN r.is_absent THEN 'Absent' WHEN r.is_pass THEN 'Pass' WHEN r.is_pass = FALSE THEN 'Fail' ELSE '' END AS "Result"
                    FROM exam_results r JOIN exam_schedules e ON e.id = r.exam_id
                    JOIN students s ON s.id = r.student_id JOIN users u ON u.id = s.user_id
                    WHERE (:program = '' OR e.program = :program) AND (:semester IS NULL OR e.semester = :semester)
                      AND (:exam IS NULL OR e.id = :exam)
                    ORDER BY e.exam_date DESC, e.exam_code, s.roll_number LIMIT :limit
                    """;
            }
            case "fees" -> {
                title = "Fee Records";
                sql = """
                    SELECT f.fee_id AS "Fee ID", f.admission_id AS "Admission ID", ad.first_name || ' ' || ad.last_name AS "Name",
                           ad.program AS "Program", f.fee_type AS "Fee Type", f.amount AS "Amount", f.concession_amount AS "Concession",
                           f.amount_paid AS "Paid", GREATEST(f.amount - f.amount_paid - f.concession_amount, 0) AS "Balance",
                           f.due_date AS "Due Date", f.paid_date AS "Paid On",
                           CASE WHEN f.status IN ('PENDING','PARTIAL') AND f.due_date < :today THEN 'OVERDUE' ELSE f.status END AS "Status"
                    FROM fees f JOIN admissions ad ON ad.admission_id = f.admission_id
                    WHERE (:program = '' OR ad.program = :program)
                      AND (:status = '' OR f.status = :status
                           OR (:status = 'OVERDUE' AND f.status IN ('PENDING','PARTIAL') AND f.due_date < :today))
                    ORDER BY f.due_date DESC, f.fee_id LIMIT :limit
                    """;
            }
            case "library" -> {
                title = "Library Issues";
                sql = """
                    SELECT b.accession_no AS "Accession No", b.title AS "Book", s.roll_number AS "Roll No", u.full_name AS "Name",
                           li.issue_date AS "Issued On", li.due_date AS "Due Date", li.return_date AS "Returned On",
                           CASE WHEN li.status = 'ISSUED' AND li.due_date < :today THEN 'OVERDUE' ELSE li.status END AS "Status",
                           li.fine AS "Fine"
                    FROM library_issues li JOIN library_books b ON b.id = li.book_id
                    JOIN students s ON s.id = li.student_id JOIN users u ON u.id = s.user_id
                    WHERE li.issue_date BETWEEN :from AND :to
                      AND (:status = '' OR (:status = 'OVERDUE' AND li.status = 'ISSUED' AND li.due_date < :today)
                           OR (:status <> 'OVERDUE' AND li.status = :status))
                    ORDER BY li.issue_date DESC LIMIT :limit
                    """;
            }
            default -> throw new IllegalArgumentException("Unknown report type: " + type);
        }

        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        jdbc.query(sql, p, rs -> {
            ResultSetMetaData md = rs.getMetaData();
            if (columns.isEmpty()) for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
            List<Object> row = new ArrayList<>(md.getColumnCount());
            for (int i = 1; i <= md.getColumnCount(); i++) {
                Object v = rs.getObject(i);
                row.add(v instanceof java.sql.Date d ? d.toLocalDate().toString() : v);
            }
            rows.add(row);
        });
        if (columns.isEmpty()) {
            // No rows: still return the headers so the UI can show an empty table
            jdbc.query(sql.replace("LIMIT :limit", "LIMIT 0"), p, (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
                ResultSetMetaData md = rs.getMetaData();
                for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
                return null;
            });
        }
        boolean truncated = rows.size() > MAX_ROWS;
        if (truncated) rows.remove(rows.size() - 1);
        return new Report(type, title, columns, rows, truncated);
    }
}
