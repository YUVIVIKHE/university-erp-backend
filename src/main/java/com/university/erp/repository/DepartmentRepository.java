package com.university.erp.repository;

import com.university.erp.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByCode(String code);
    boolean existsByName(String name);

    @Query("""
        SELECT d FROM Department d
        WHERE (:search = '' OR LOWER(d.name) LIKE LOWER(CONCAT('%',:search,'%'))
                            OR LOWER(d.code) LIKE LOWER(CONCAT('%',:search,'%'))
                            OR LOWER(d.deanName) LIKE LOWER(CONCAT('%',:search,'%')))
          AND (:status IS NULL OR d.status = :status)
        ORDER BY d.name ASC
        """)
    Page<Department> search(
        @Param("search") String search,
        @Param("status") Department.Status status,
        Pageable pageable
    );
}
