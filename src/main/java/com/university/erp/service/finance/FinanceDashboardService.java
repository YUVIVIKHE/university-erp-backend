package com.university.erp.service.finance;

import com.university.erp.dto.finance.FinanceDashboard;
import com.university.erp.entity.Fee;
import com.university.erp.repository.finance.FinanceDashboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;

@Service
public class FinanceDashboardService {
    private final FinanceDashboardRepository repository;
    private final Clock clock;

    @Autowired
    public FinanceDashboardService(FinanceDashboardRepository repository,
        @Value("${app.finance.time-zone:Asia/Kolkata}") String timeZone) {
        this(repository, Clock.system(ZoneId.of(timeZone)));
    }

    FinanceDashboardService(FinanceDashboardRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FinanceDashboard dashboard() {
        LocalDate today = LocalDate.now(clock);
        var totals = repository.totals(today);
        return new FinanceDashboard(today, totals.getTotalBilled(), totals.getTotalCollected(), totals.getTotalConcessions(), totals.getTotalRefunded(),
            totals.getOutstanding(), totals.getOverdueAmount(), totals.getWaivedBalance(),
            totals.getTotalRecords(), totals.getPaid(), totals.getPending(), totals.getPartial(), totals.getOverdue(), totals.getWaived(),
            repository.findTop8ByOrderByCreatedAtDescIdDesc().stream().map(f -> item(f, today)).toList(),
            repository.upcoming(today, today.plusDays(7), Fee.Status.WAIVED, PageRequest.of(0, 8)).stream().map(f -> item(f, today)).toList(),
            totals.getDueSoon(), totals.getDueSoonCount(), java.util.List.of(
                new FinanceDashboard.AgingBucket("1–30 days", totals.getAging30()),
                new FinanceDashboard.AgingBucket("31–60 days", totals.getAging60()),
                new FinanceDashboard.AgingBucket("61–90 days", totals.getAging90()),
                new FinanceDashboard.AgingBucket("Over 90 days", totals.getAgingOlder())), OffsetDateTime.now(clock));
    }

    static FinanceDashboard.FeeItem item(Fee fee, LocalDate today) {
        BigDecimal remaining = fee.netAmount().subtract(fee.getAmountPaid()).max(BigDecimal.ZERO);
        String status = fee.getStatus() == Fee.Status.WAIVED ? "WAIVED"
            : remaining.signum() == 0 ? "PAID"
            : fee.getDueDate().isBefore(today) ? "OVERDUE"
            : fee.getAmountPaid().signum() > 0 ? "PARTIAL" : "PENDING";
        var admission = fee.getAdmission();
        return new FinanceDashboard.FeeItem(fee.getFeeId(), admission.getAdmissionId(),
            admission.getFirstName() + " " + admission.getLastName(), admission.getProgram(),
            fee.getFeeType(), fee.getAmount(), fee.getAmountPaid(), fee.getConcessionAmount(),
            status.equals("WAIVED") ? BigDecimal.ZERO : remaining, fee.getDueDate(), status, fee.getCreatedAt());
    }
}
