package com.university.erp.service.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Live numbers for the admin home page. Every figure comes from the database. */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final InstitutionSettingsService settings;

    public record Counts(long students, long activeStudents, long faculty, long programs, long courses, long subjects,
                         long pendingAdmissions, long upcomingExams, long activeNotices, long users) {}

    public record Fees(BigDecimal billed, BigDecimal collected, BigDecimal concessions, BigDecimal outstanding,
                       long overdueRecords, BigDecimal collectionRate) {}

    public record Attendance(long sessions, long marked, long present, BigDecimal rate, int threshold,
                             long studentsBelowThreshold) {}

    public record Library(long titles, long issued, long overdue) {}

    public record View(LocalDate asOf, String institutionName, String academicYear, Counts counts, Fees fees,
                       Attendance attendance, Library library,
                       List<Map<String, Object>> studentsByProgram,
                       List<Map<String, Object>> admissionsByStatus,
                       List<Map<String, Object>> attendanceTrend,
                       List<Map<String, Object>> upcomingExams,
                       List<Map<String, Object>> recentActivity) {}

    @Transactional(readOnly = true)
    public View get() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Map<String, Object> t = Map.of("today", today, "from", today.minusDays(29));
        var s = settings.read();
        int threshold = Integer.parseInt(s.get("attendanceThreshold"));

        var c = jdbc.queryForMap("""
            SELECT (SELECT COUNT(*) FROM students) students,
                   (SELECT COUNT(*) FROM students WHERE is_active) active_students,
                   (SELECT COUNT(*) FROM faculty WHERE is_active) faculty,
                   (SELECT COUNT(*) FROM programs) programs,
                   (SELECT COUNT(*) FROM courses) courses,
                   (SELECT COUNT(*) FROM subjects) subjects,
                   (SELECT COUNT(*) FROM admissions WHERE status IN ('PENDING','REVIEW')) pending_adm,
                   (SELECT COUNT(*) FROM exam_schedules WHERE exam_date >= :today AND status IN ('SCHEDULED','ONGOING')) exams,
                   (SELECT COUNT(*) FROM notices WHERE publish_at <= NOW() AND (expires_at IS NULL OR expires_at > NOW())) notices,
                   (SELECT COUNT(*) FROM users WHERE is_active) users
            """, t);
        var counts = new Counts(n(c, "students"), n(c, "active_students"), n(c, "faculty"), n(c, "programs"),
            n(c, "courses"), n(c, "subjects"), n(c, "pending_adm"), n(c, "exams"), n(c, "notices"), n(c, "users"));

        var f = jdbc.queryForMap("""
            SELECT COALESCE(SUM(amount),0) billed, COALESCE(SUM(amount_paid),0) collected,
                   COALESCE(SUM(concession_amount),0) concessions,
                   COALESCE(SUM(GREATEST(amount - amount_paid - concession_amount, 0)) FILTER (WHERE status <> 'WAIVED'),0) outstanding,
                   COUNT(*) FILTER (WHERE status = 'OVERDUE' OR (status IN ('PENDING','PARTIAL') AND due_date < :today)) overdue
            FROM fees
            """, t);
        BigDecimal billed = (BigDecimal) f.get("billed");
        BigDecimal collected = (BigDecimal) f.get("collected");
        BigDecimal net = billed.subtract((BigDecimal) f.get("concessions"));
        var fees = new Fees(billed, collected, (BigDecimal) f.get("concessions"), (BigDecimal) f.get("outstanding"),
            n(f, "overdue"), net.signum() == 0 ? BigDecimal.ZERO : collected.multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP));

        var a = jdbc.queryForMap("""
            SELECT COUNT(DISTINCT (date, subject_code, time_slot)) sessions, COUNT(*) marked,
                   COUNT(*) FILTER (WHERE status IN ('PRESENT','LATE')) present
            FROM attendance WHERE date BETWEEN :from AND :today
            """, t);
        long marked = n(a, "marked");
        long below = jdbc.queryForObject("""
            SELECT COUNT(*) FROM (
              SELECT student_id FROM attendance WHERE date BETWEEN :from AND :today
              GROUP BY student_id
              HAVING COUNT(*) FILTER (WHERE status IN ('PRESENT','LATE')) * 100.0 / COUNT(*) < :threshold
            ) x
            """, Map.of("from", today.minusDays(29), "today", today, "threshold", threshold), Long.class);
        var attendance = new Attendance(n(a, "sessions"), marked, n(a, "present"),
            marked == 0 ? null : BigDecimal.valueOf(n(a, "present") * 100.0 / marked).setScale(1, RoundingMode.HALF_UP),
            threshold, below);

        var l = jdbc.queryForMap("""
            SELECT (SELECT COUNT(*) FROM library_books) titles,
                   (SELECT COUNT(*) FROM library_issues WHERE status = 'ISSUED') issued,
                   (SELECT COUNT(*) FROM library_issues WHERE status = 'ISSUED' AND due_date < :today) overdue
            """, t);
        var library = new Library(n(l, "titles"), n(l, "issued"), n(l, "overdue"));

        var byProgram = jdbc.queryForList("""
            SELECT program AS label, COUNT(*) AS value FROM students WHERE is_active
            GROUP BY program ORDER BY value DESC, program LIMIT 8
            """, Map.of());
        var byStatus = jdbc.queryForList("SELECT status AS label, COUNT(*) AS value FROM admissions GROUP BY status ORDER BY status", Map.of());
        var trend = jdbc.queryForList("""
            SELECT to_char(d::date, 'DD Mon') AS label,
                   ROUND(COUNT(a.id) FILTER (WHERE a.status IN ('PRESENT','LATE')) * 100.0 / NULLIF(COUNT(a.id), 0), 1) AS value
            FROM generate_series(CAST(:today AS date) - 13, CAST(:today AS date), interval '1 day') d
            LEFT JOIN attendance a ON a.date = d::date
            GROUP BY d ORDER BY d
            """, t);
        var exams = jdbc.queryForList("""
            SELECT id, exam_code AS "examCode", title, program, semester, exam_date AS "examDate",
                   start_time AS "startTime", venue
            FROM exam_schedules WHERE exam_date >= :today AND status IN ('SCHEDULED','ONGOING')
            ORDER BY exam_date, start_time LIMIT 5
            """, t);
        var activity = jdbc.queryForList("""
            SELECT a.id, a.action, a.entity_type AS "entityType", a.created_at AS "createdAt",
                   COALESCE(u.full_name, 'System') AS actor
            FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id
            ORDER BY a.created_at DESC LIMIT 10
            """, Map.of());

        return new View(today, s.get("institutionName"), s.get("academicYear"), counts, fees, attendance, library,
            byProgram, byStatus, trend, exams, activity);
    }

    private static long n(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o == null ? 0 : ((Number) o).longValue();
    }
}
