package com.university.erp.repository;

import com.university.erp.entity.ExamSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {

    Optional<ExamSchedule> findByExamCode(String examCode);
    boolean existsByExamCode(String examCode);

    @Query("""
        SELECT e FROM ExamSchedule e
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(e.title)    LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(e.examCode) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:program  IS NULL OR :program  = '' OR e.program  = :program)
        AND (:semester IS NULL OR e.semester = :semester)
        AND (:examType IS NULL OR :examType = '' OR CAST(e.examType AS string) = :examType)
        AND (:status   IS NULL OR :status   = '' OR CAST(e.status   AS string) = :status)
        AND (:academicYear IS NULL OR :academicYear = '' OR e.academicYear = :academicYear)
        """)
    Page<ExamSchedule> search(
        @Param("search")       String  search,
        @Param("program")      String  program,
        @Param("semester")     Short   semester,
        @Param("examType")     String  examType,
        @Param("status")       String  status,
        @Param("academicYear") String  academicYear,
        Pageable pageable
    );
}
