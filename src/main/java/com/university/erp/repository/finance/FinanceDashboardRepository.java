package com.university.erp.repository.finance;

import com.university.erp.entity.Fee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FinanceDashboardRepository extends Repository<Fee, Long> {
    interface Totals {
        BigDecimal getTotalRefunded(); BigDecimal getTotalConcessions(); BigDecimal getTotalBilled(); BigDecimal getTotalCollected(); BigDecimal getOutstanding();
        BigDecimal getOverdueAmount(); BigDecimal getWaivedBalance();
        long getTotalRecords(); long getPaid(); long getPending(); long getPartial(); long getOverdue(); long getWaived();
        BigDecimal getDueSoon(); long getDueSoonCount();
        BigDecimal getAging30(); BigDecimal getAging60(); BigDecimal getAging90(); BigDecimal getAgingOlder();
    }

    @Query(value = """
        SELECT COALESCE(SUM(amount), 0) AS "totalBilled",
               COALESCE(SUM(amount_paid), 0) AS "totalCollected", COALESCE(SUM(concession_amount),0) AS "totalConcessions", (SELECT COALESCE(SUM(amount),0) FROM finance_refunds WHERE status='PAID') AS "totalRefunded",
               COALESCE(SUM(balance) FILTER (WHERE status <> 'WAIVED'), 0) AS "outstanding",
               COALESCE(SUM(balance) FILTER (WHERE status <> 'WAIVED' AND due_date < CAST(:today AS date)), 0) AS "overdueAmount",
               COALESCE(SUM(balance) FILTER (WHERE status = 'WAIVED'), 0) AS "waivedBalance",
               COUNT(*) AS "totalRecords",
               COUNT(*) FILTER (WHERE status <> 'WAIVED' AND balance = 0) AS "paid",
               COUNT(*) FILTER (WHERE status <> 'WAIVED' AND balance > 0 AND due_date >= CAST(:today AS date) AND amount_paid = 0) AS "pending",
               COUNT(*) FILTER (WHERE status <> 'WAIVED' AND balance > 0 AND due_date >= CAST(:today AS date) AND amount_paid > 0) AS "partial",
               COUNT(*) FILTER (WHERE status <> 'WAIVED' AND balance > 0 AND due_date < CAST(:today AS date)) AS "overdue",
               COUNT(*) FILTER (WHERE status = 'WAIVED') AS "waived",
               COALESCE(SUM(balance) FILTER (WHERE status <> 'WAIVED' AND due_date BETWEEN CAST(:today AS date) AND CAST(:today AS date) + 7), 0) AS "dueSoon",
               COUNT(*) FILTER (WHERE status <> 'WAIVED' AND balance > 0 AND due_date BETWEEN CAST(:today AS date) AND CAST(:today AS date) + 7) AS "dueSoonCount",
               COALESCE(SUM(balance) FILTER (WHERE status <> 'WAIVED' AND CAST(:today AS date) - due_date BETWEEN 1 AND 30), 0) AS "aging30",
               COALESCE(SUM(balance) FILTER (WHERE status <> 'WAIVED' AND CAST(:today AS date) - due_date BETWEEN 31 AND 60), 0) AS "aging60",
               COALESCE(SUM(balance) FILTER (WHERE status <> 'WAIVED' AND CAST(:today AS date) - due_date BETWEEN 61 AND 90), 0) AS "aging90",
               COALESCE(SUM(balance) FILTER (WHERE status <> 'WAIVED' AND CAST(:today AS date) - due_date > 90), 0) AS "agingOlder"
        FROM (SELECT amount, concession_amount, amount_paid, due_date, status, GREATEST(amount - concession_amount - amount_paid, 0) AS balance FROM fees) balances
        """, nativeQuery = true)
    Totals totals(@Param("today") LocalDate today);

    @EntityGraph(attributePaths = "admission")
    List<Fee> findTop8ByOrderByCreatedAtDescIdDesc();

    @Query("""
        select f from Fee f join fetch f.admission
        where f.status <> :waived
          and f.amount - f.concessionAmount > f.amountPaid and f.dueDate between CAST(:today AS date) and :until
        order by f.dueDate asc, f.id asc
        """)
    List<Fee> upcoming(@Param("today") LocalDate today, @Param("until") LocalDate until,
        @Param("waived") Fee.Status waived, Pageable pageable);
}
