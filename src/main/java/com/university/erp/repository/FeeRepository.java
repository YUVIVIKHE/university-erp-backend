package com.university.erp.repository;

import com.university.erp.entity.Fee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeRepository extends JpaRepository<Fee, Long> {
    java.util.Optional<Fee> findByFeeId(String feeId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fee f WHERE f.feeId=:feeId")
    java.util.Optional<Fee> findByFeeIdForUpdate(@Param("feeId") String feeId);

    boolean existsByFeeId(String feeId);

    @Query("""
        SELECT f FROM Fee f
        JOIN f.admission a
        WHERE (:search = '' OR
            LOWER(a.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(a.lastName)  LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(a.admissionId) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(f.feeId) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(f.feeType) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        AND (:status IS NULL OR f.status = :status)
        ORDER BY f.createdAt DESC
        """)
    Page<Fee> search(
        @Param("search") String search,
        @Param("status") Fee.Status status,
        Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fee f")
    java.math.BigDecimal totalAmount();

    @Query("SELECT COALESCE(SUM(f.amountPaid), 0) FROM Fee f")
    java.math.BigDecimal totalCollected();

    long countByStatus(Fee.Status status);

    boolean existsByAdmission_AdmissionId(String admissionId);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fee f WHERE f.admission.admissionId = :admissionId")
    java.math.BigDecimal totalAmountByAdmission(@Param("admissionId") String admissionId);

    @Query("SELECT COALESCE(SUM(f.amountPaid), 0) FROM Fee f WHERE f.admission.admissionId = :admissionId")
    java.math.BigDecimal totalPaidByAdmission(@Param("admissionId") String admissionId);

    long countByAdmission_AdmissionId(String admissionId);

    @Query("SELECT f FROM Fee f JOIN f.admission a WHERE LOWER(a.email) = LOWER(:email) ORDER BY f.createdAt DESC")
    java.util.List<Fee> findByAdmissionEmail(@Param("email") String email);
}
