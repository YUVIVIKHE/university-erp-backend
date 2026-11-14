package com.university.erp.repository;

import com.university.erp.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    boolean existsBySubjectCode(String subjectCode);

    @Query("""
        SELECT s FROM Subject s
        WHERE (:search = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%',:search,'%'))
                            OR LOWER(s.subjectCode) LIKE LOWER(CONCAT('%',:search,'%'))
                            OR LOWER(s.facultyName) LIKE LOWER(CONCAT('%',:search,'%')))
          AND (:course   = '' OR s.course   = :course)
          AND (:semester IS NULL OR s.semester = :semester)
          AND (:year     IS NULL OR s.year     = :year)
          AND (:status   IS NULL OR s.status   = :status)
        ORDER BY s.year ASC, s.semester ASC, s.name ASC
        """)
    Page<Subject> search(
        @Param("search")   String search,
        @Param("course")   String course,
        @Param("semester") Integer semester,
        @Param("year")     Integer year,
        @Param("status")   Subject.Status status,
        Pageable pageable
    );
}
