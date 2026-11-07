package com.university.erp.repository.finance;

import com.university.erp.entity.Admission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface StudentFeeAccountRepository extends Repository<Admission, Long> {
    interface Account {
        String getAdmissionId(); String getStudentName(); String getProgram();
        int getSemester(); int getBatchYear(); String getAdmissionStatus();
        long getFeeCount(); BigDecimal getBilled(); BigDecimal getPaid(); BigDecimal getConcessions();
        BigDecimal getOutstanding(); BigDecimal getOverdue(); BigDecimal getWaived();
    }
    String FILTER = """
        FROM admissions a WHERE
          (a.status = 'APPROVED' OR EXISTS (SELECT 1 FROM fees f WHERE f.admission_id = a.admission_id))
          AND (:id = '' OR a.admission_id = :id)
          AND (:search = '' OR lower(a.first_name || ' ' || a.last_name) LIKE lower('%' || :search || '%')
              OR lower(a.admission_id) LIKE lower('%' || :search || '%'))
          AND (:program = '' OR lower(a.program) = lower(:program))
          AND (:semester = 0 OR a.semester = :semester)
          AND (:batch = 0 OR a.batch_year = :batch)
        """;
    @Query(value = """
        SELECT a.admission_id AS "admissionId", a.first_name || ' ' || a.last_name AS "studentName",
          a.program, a.semester, a.batch_year AS "batchYear", a.status AS "admissionStatus",
          totals.* FROM (SELECT a.*
        """ + FILTER + """
          ORDER BY a.id DESC LIMIT :limit OFFSET :offset) a
        CROSS JOIN LATERAL (
          SELECT count(*) AS "feeCount", coalesce(sum(f.amount),0) AS billed,
            coalesce(sum(f.amount_paid),0) AS paid, coalesce(sum(f.concession_amount),0) AS concessions,
            coalesce(sum(greatest(f.amount-f.concession_amount-f.amount_paid,0)) FILTER (WHERE f.status <> 'WAIVED'),0) AS outstanding,
            coalesce(sum(greatest(f.amount-f.concession_amount-f.amount_paid,0)) FILTER (WHERE f.status <> 'WAIVED' AND f.due_date < :today),0) AS overdue,
            coalesce(sum(greatest(f.amount-f.concession_amount-f.amount_paid,0)) FILTER (WHERE f.status = 'WAIVED'),0) AS waived
          FROM fees f WHERE f.admission_id = a.admission_id
        ) totals ORDER BY a.id DESC
        """, nativeQuery=true)
    java.util.List<Account> accounts(@Param("search") String search, @Param("program") String program,
        @Param("semester") int semester, @Param("batch") int batch, @Param("id") String id,
        @Param("today") LocalDate today, @Param("limit") int limit, @Param("offset") long offset);

    @Query(value="SELECT count(*) " + FILTER, nativeQuery=true)
    long countAccounts(@Param("search") String search, @Param("program") String program,
        @Param("semester") int semester, @Param("batch") int batch, @Param("id") String id);
}
