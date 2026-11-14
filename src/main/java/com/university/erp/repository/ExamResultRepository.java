package com.university.erp.repository;

import com.university.erp.entity.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {

    List<ExamResult> findByExamId(Long examId);

    Optional<ExamResult> findByExamIdAndStudentId(Long examId, Long studentId);

    @Query("""
        SELECT r FROM ExamResult r
        JOIN r.exam e
        JOIN r.student s
        WHERE s.rollNumber = :rollNumber
        ORDER BY e.examDate DESC
        """)
    List<ExamResult> findByStudentRollNumber(@Param("rollNumber") String rollNumber);

    @Query("""
        SELECT r FROM ExamResult r
        JOIN r.exam e
        JOIN r.student s
        WHERE s.rollNumber = :rollNumber
          AND e.program    = :program
          AND e.semester   = :semester
        ORDER BY e.examDate DESC
        """)
    List<ExamResult> findByStudentAndSemester(
        @Param("rollNumber") String rollNumber,
        @Param("program")    String program,
        @Param("semester")   Short  semester
    );

    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
}
