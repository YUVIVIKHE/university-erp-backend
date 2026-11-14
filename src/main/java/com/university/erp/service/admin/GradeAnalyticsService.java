package com.university.erp.service.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read-only grade analytics over exam_results, across all exams. */
@Service
@RequiredArgsConstructor
public class GradeAnalyticsService {

    private final NamedParameterJdbcTemplate jdbc;

    public record Row(Long id, String rollNumber, String studentName, String program, Integer semester,
                      Long examId, String examCode, String examTitle, String examType, LocalDate examDate,
                      BigDecimal marksObtained, BigDecimal totalMarks, BigDecimal percentage, String grade,
                      BigDecimal gradePoints, boolean absent, Boolean pass) {}

    public record Summary(long results, long appeared, long absent, long passed, long failed,
                          BigDecimal passRate, BigDecimal averagePercentage, BigDecimal highestPercentage,
                          List<Map<String, Object>> gradeDistribution) {}

    public record Result(List<Row> content, long totalElements, int totalPages, int number, Summary summary) {}

    public record ExamOption(Long id, String examCode, String title, String program, Integer semester) {}

    private static final String FROM = """
        FROM exam_results r
        JOIN exam_schedules e ON e.id = r.exam_id
        JOIN students s ON s.id = r.student_id
        JOIN users u ON u.id = s.user_id
        WHERE (:program = '' OR e.program = :program)
          AND (:semester IS NULL OR e.semester = :semester)
          AND (:exam IS NULL OR e.id = :exam)
          AND (:search = '' OR LOWER(u.full_name) LIKE :like OR LOWER(s.roll_number) LIKE :like)
          AND (:outcome = '' OR (:outcome = 'PASS' AND r.is_pass IS TRUE)
               OR (:outcome = 'FAIL' AND r.is_pass IS FALSE AND r.is_absent IS FALSE)
               OR (:outcome = 'ABSENT' AND r.is_absent IS TRUE))
        """;

    @Transactional(readOnly = true)
    public Result search(String program, Integer semester, Long examId, String search, String outcome, int page) {
        int size = 25;
        String q = search == null ? "" : search.trim().toLowerCase();
        var p = new MapSqlParameterSource("program", program == null ? "" : program)
            .addValue("semester", semester, java.sql.Types.INTEGER)
            .addValue("exam", examId, java.sql.Types.BIGINT)
            .addValue("search", q).addValue("like", "%" + q + "%")
            .addValue("outcome", outcome == null ? "" : outcome.toUpperCase())
            .addValue("limit", size).addValue("offset", Math.max(page, 0) * size);

        var agg = jdbc.queryForMap("""
            SELECT COUNT(*) total,
                   COUNT(*) FILTER (WHERE NOT r.is_absent) appeared,
                   COUNT(*) FILTER (WHERE r.is_absent) absent,
                   COUNT(*) FILTER (WHERE r.is_pass IS TRUE) passed,
                   COUNT(*) FILTER (WHERE r.is_pass IS FALSE AND NOT r.is_absent) failed,
                   AVG(r.marks_obtained * 100.0 / NULLIF(e.total_marks, 0)) FILTER (WHERE NOT r.is_absent) avg_pct,
                   MAX(r.marks_obtained * 100.0 / NULLIF(e.total_marks, 0)) FILTER (WHERE NOT r.is_absent) max_pct
            """ + FROM, p);
        var dist = jdbc.queryForList("SELECT COALESCE(NULLIF(r.grade, ''), CASE WHEN r.is_absent THEN 'AB' ELSE '-' END) AS grade, COUNT(*) AS count "
            + FROM + " GROUP BY 1 ORDER BY 1", p);

        long total = num(agg.get("total"));
        long appeared = num(agg.get("appeared"));
        long passed = num(agg.get("passed"));
        var summary = new Summary(total, appeared, num(agg.get("absent")), passed, num(agg.get("failed")),
            appeared == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(passed * 100.0 / appeared).setScale(1, RoundingMode.HALF_UP),
            round(agg.get("avg_pct")), round(agg.get("max_pct")), dist);

        var rows = jdbc.query("""
            SELECT r.id, s.roll_number, u.full_name, s.program, e.semester, e.id exam_id, e.exam_code, e.title,
                   e.exam_type, e.exam_date, r.marks_obtained, e.total_marks,
                   ROUND(r.marks_obtained * 100.0 / NULLIF(e.total_marks, 0), 1) pct,
                   r.grade, r.grade_points, r.is_absent, r.is_pass
            """ + FROM + " ORDER BY e.exam_date DESC, s.roll_number LIMIT :limit OFFSET :offset", p,
            (rs, i) -> new Row(rs.getLong("id"), rs.getString("roll_number"), rs.getString("full_name"),
                rs.getString("program"), rs.getInt("semester"), rs.getLong("exam_id"), rs.getString("exam_code"),
                rs.getString("title"), rs.getString("exam_type"), rs.getObject("exam_date", LocalDate.class),
                rs.getBigDecimal("marks_obtained"), rs.getBigDecimal("total_marks"), rs.getBigDecimal("pct"),
                rs.getString("grade"), rs.getBigDecimal("grade_points"), rs.getBoolean("is_absent"),
                (Boolean) rs.getObject("is_pass")));
        return new Result(rows, total, (int) Math.ceil(total / (double) size), Math.max(page, 0), summary);
    }

    @Transactional(readOnly = true)
    public List<ExamOption> exams() {
        return jdbc.query("SELECT id, exam_code, title, program, semester FROM exam_schedules ORDER BY exam_date DESC LIMIT 500",
            Map.of(), (rs, i) -> new ExamOption(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
    }

    @Transactional(readOnly = true)
    public List<String> programs() {
        return jdbc.queryForList("""
            SELECT program FROM (
              SELECT program FROM students UNION SELECT program FROM exam_schedules UNION SELECT program FROM courses
            ) p WHERE program IS NOT NULL AND program <> '' ORDER BY 1
            """, Map.of(), String.class);
    }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
    private static BigDecimal round(Object o) {
        return o == null ? null : new BigDecimal(o.toString()).setScale(1, RoundingMode.HALF_UP);
    }
}
