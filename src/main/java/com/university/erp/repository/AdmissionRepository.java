package com.university.erp.repository;

import com.university.erp.entity.Admission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {
    Optional<Admission> findFirstByEmailIgnoreCaseAndStatus(String email, Admission.Status status);

    Optional<Admission> findByAdmissionId(String admissionId);

    boolean existsByAdmissionId(String admissionId);

    boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.Query("""
        SELECT a FROM Admission a
        WHERE NOT EXISTS (SELECT 1 FROM Fee f WHERE f.admission = a)
        ORDER BY a.createdAt DESC
        """)
    java.util.List<Admission> findAdmissionsWithoutFees(org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT a FROM Admission a
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(a.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(a.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(a.email)     LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(a.admissionId) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(a.program)   LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:status IS NULL OR a.status = :status)
        """)
    Page<Admission> search(
        @Param("search") String search,
        @Param("status") Admission.Status status,
        Pageable pageable
    );
}
