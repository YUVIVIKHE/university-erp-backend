package com.university.erp.repository;

import com.university.erp.entity.Faculty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    Optional<Faculty> findByUserId(Long userId);
    Optional<Faculty> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);

    @Query("""
        SELECT f FROM Faculty f JOIN f.user u
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(u.fullName)   LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.email)      LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(f.employeeId) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(f.department) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:department IS NULL OR :department = '' OR f.department = :department)
        AND (:active IS NULL OR f.isActive = :active)
        """)
    Page<Faculty> search(
        @Param("search")     String  search,
        @Param("department") String  department,
        @Param("active")     Boolean active,
        Pageable pageable
    );
}
