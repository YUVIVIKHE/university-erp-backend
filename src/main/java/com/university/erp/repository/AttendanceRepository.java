package com.university.erp.repository;

import com.university.erp.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByStudentIdAndSubjectCodeAndDate(Long studentId, String subjectCode, LocalDate date);

    Optional<Attendance> findByStudentIdAndSubjectCodeAndDateAndTimeSlot(
        Long studentId, String subjectCode, LocalDate date, String timeSlot);

    /** Distinct (date, subjectCode, subjectName, timeSlot) sessions for a program/batch, latest first */
    @Query(value = """
        SELECT DISTINCT a.date, a.subject_code, a.subject_name, a.time_slot,
               COUNT(CASE WHEN a.status='PRESENT' THEN 1 END) OVER w AS present_count,
               COUNT(CASE WHEN a.status='ABSENT'  THEN 1 END) OVER w AS absent_count,
               COUNT(*)                                        OVER w AS total_count
        FROM attendance a
        JOIN students s ON s.id = a.student_id
        WHERE s.program = :program
          AND (CAST(:batchYear AS smallint) IS NULL OR s.batch_year = CAST(:batchYear AS smallint))
        WINDOW w AS (PARTITION BY a.date, a.subject_code, a.time_slot)
        ORDER BY a.date DESC, a.time_slot
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findRecentSessions(
        @Param("program") String program,
        @Param("batchYear") Short batchYear,
        @Param("limit") int limit
    );

    @Query("""
        SELECT a FROM Attendance a JOIN a.student s
        WHERE s.program = :program
          AND (:batchYear IS NULL OR s.batchYear = :batchYear)
          AND a.subjectCode = :subjectCode
          AND a.date = :date
          AND a.timeSlot = :timeSlot
        ORDER BY s.rollNumber
        """)
    List<Attendance> findSession(
        @Param("program") String program,
        @Param("batchYear") Short batchYear,
        @Param("subjectCode") String subjectCode,
        @Param("date") LocalDate date,
        @Param("timeSlot") String timeSlot
    );

    @Query("""
        SELECT COUNT(a) FROM Attendance a
        WHERE a.student.id = :studentId
          AND a.subjectCode = :subjectCode
          AND a.status = :status
        """)
    long countByStatus(@Param("studentId") Long studentId, @Param("subjectCode") String subjectCode,
                       @Param("status") Attendance.Status status);

    default long countPresent(Long studentId, String subjectCode) {
        return countByStatus(studentId, subjectCode, Attendance.Status.PRESENT);
    }

    @Query("""
        SELECT COUNT(a) FROM Attendance a
        WHERE a.student.id = :studentId
          AND a.subjectCode = :subjectCode
        """)
    long countTotal(@Param("studentId") Long studentId, @Param("subjectCode") String subjectCode);

    /** All attendance records for a student, newest first */
    @Query("""
        SELECT a FROM Attendance a
        WHERE a.student.id = :studentId
        ORDER BY a.date DESC, a.timeSlot ASC
        """)
    List<Attendance> findAllByStudentId(@Param("studentId") Long studentId);

    /** Per-subject attendance summary for a student's portal view */
    @Query(value = """
        SELECT a.subject_code  AS subjectCode,
               a.subject_name  AS subjectName,
               COUNT(*)        AS total,
               SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END) AS present,
               SUM(CASE WHEN a.status = 'ABSENT'  THEN 1 ELSE 0 END) AS absent
        FROM attendance a
        WHERE a.student_id = :studentId
        GROUP BY a.subject_code, a.subject_name
        ORDER BY a.subject_name
        """, nativeQuery = true)
    List<Object[]> getSubjectSummaryRaw(@Param("studentId") Long studentId);
}
