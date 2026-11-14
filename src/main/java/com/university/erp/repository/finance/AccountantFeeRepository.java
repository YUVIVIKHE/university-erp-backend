package com.university.erp.repository.finance;

import com.university.erp.entity.Fee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface AccountantFeeRepository extends Repository<Fee, Long> {
    @Query("""
        select f from Fee f join fetch f.admission a
        where (:search = '' or lower(concat(a.firstName, ' ', a.lastName)) like lower(concat('%', :search, '%'))
          or lower(a.admissionId) like lower(concat('%', :search, '%'))
          or lower(f.feeId) like lower(concat('%', :search, '%'))
          or lower(a.program) like lower(concat('%', :search, '%'))
          or lower(f.feeType) like lower(concat('%', :search, '%')))
        and (:status = '' or
          (case when f.status = :waived then 'WAIVED'
           when f.amountPaid >= f.amount - f.concessionAmount then 'PAID'
           when f.dueDate < :today then 'OVERDUE'
           when f.amountPaid > 0 then 'PARTIAL' else 'PENDING' end) = :status)
        order by f.createdAt desc, f.id desc
        """)
    Page<Fee> search(@Param("search") String search, @Param("status") String status,
        @Param("today") LocalDate today, @Param("waived") Fee.Status waived, Pageable pageable);

    @Query("select f from Fee f join fetch f.admission where f.feeId = :id")
    Optional<Fee> find(@Param("id") String id);
    Fee save(Fee fee);
    @Query("select f from Fee f join fetch f.admission a where a.admissionId = :id order by f.dueDate, f.id")
    Page<Fee> accountCharges(@Param("id") String id, Pageable pageable);
}
