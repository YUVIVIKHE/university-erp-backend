package com.university.erp.repository;

import com.university.erp.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    @Query("SELECT s FROM Student s JOIN FETCH s.user WHERE s.rollNumber = :rollNumber")
    Optional<Student> findByRollNumber(@Param("rollNumber") String rollNumber);

    @Query("SELECT s FROM Student s JOIN FETCH s.user WHERE s.user.id = :userId")
    Optional<Student> findByUserId(@Param("userId") Long userId);
    boolean existsByRollNumber(String rollNumber);

    @Query("""
        SELECT s FROM Student s JOIN s.user u
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(u.fullName)    LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.email)       LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(s.rollNumber)  LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(s.program)     LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:program IS NULL OR :program = '' OR s.program = :program)
        AND (:active IS NULL OR s.isActive = :active)
        """)
    Page<Student> search(
        @Param("search")  String search,
        @Param("program") String program,
        @Param("active")  Boolean active,
        Pageable pageable
    );
}
