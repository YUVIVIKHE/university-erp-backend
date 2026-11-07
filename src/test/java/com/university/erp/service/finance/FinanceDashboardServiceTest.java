package com.university.erp.service.finance;

import com.university.erp.entity.Admission;
import com.university.erp.entity.Fee;
import com.university.erp.repository.finance.FinanceDashboardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceDashboardServiceTest {
    private final LocalDate today = LocalDate.of(2026, 11, 6);

    private Fee fee(Fee.Status status, String paid, LocalDate due) {
        return Fee.builder().feeId("FEE-TEST").admission(Admission.builder().admissionId("ADM-TEST")
                .firstName("Test").lastName("Student").program("B.Tech").build())
            .feeType("Tuition").amount(new BigDecimal("1000.00")).amountPaid(new BigDecimal(paid))
            .dueDate(due).status(status).createdAt(OffsetDateTime.parse("2026-11-01T00:00:00Z")).build();
    }

    @Test void pastDuePartialPaymentBecomesOverdueWithoutChangingStoredStatus() {
        var fee = fee(Fee.Status.PARTIAL, "200.00", today.minusDays(1));
        var item = FinanceDashboardService.item(fee, today);
        assertEquals("OVERDUE", item.status()); assertEquals(new BigDecimal("800.00"), item.balance());
        assertEquals(Fee.Status.PARTIAL, fee.getStatus());
    }

    @Test void dueTodayIsNotOverdueAndFullPaymentIsPaid() {
        assertEquals("PENDING", FinanceDashboardService.item(fee(Fee.Status.PENDING, "0.00", today), today).status());
        assertEquals("PARTIAL", FinanceDashboardService.item(fee(Fee.Status.PARTIAL, "200.00", today), today).status());
        assertEquals("PAID", FinanceDashboardService.item(fee(Fee.Status.PENDING, "1000.00", today.minusDays(1)), today).status());
    }

    @Test void waivedFeesHaveNoCollectibleBalanceAndOverpaymentsNeverShowNegativeDebt() {
        var waived = FinanceDashboardService.item(fee(Fee.Status.WAIVED, "200.00", today.minusDays(1)), today);
        assertEquals("WAIVED", waived.status()); assertEquals(BigDecimal.ZERO, waived.balance());
        var overpaid = FinanceDashboardService.item(fee(Fee.Status.PAID, "1200.00", today), today);
        assertEquals(BigDecimal.ZERO, overpaid.balance()); assertEquals("PAID", overpaid.status());
    }

    @Test void dashboardUsesInstitutionDateAndSevenDayUpcomingWindow() {
        var repository = mock(FinanceDashboardRepository.class);
        var totals = mock(FinanceDashboardRepository.Totals.class);
        when(repository.totals(today)).thenReturn(totals);
        when(totals.getOutstanding()).thenReturn(new BigDecimal("800.00"));
        when(totals.getDueSoon()).thenReturn(new BigDecimal("1000.00"));
        when(totals.getDueSoonCount()).thenReturn(1L);
        when(totals.getAging30()).thenReturn(new BigDecimal("800.00"));
        when(repository.findTop8ByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(fee(Fee.Status.PENDING, "0.00", today)));
        when(repository.upcoming(today, today.plusDays(7), Fee.Status.WAIVED, PageRequest.of(0,8))).thenReturn(List.of());
        // UTC is still Nov 5; Kolkata has already entered Nov 6.
        var clock = Clock.fixed(Instant.parse("2026-11-05T20:00:00Z"), ZoneId.of("Asia/Kolkata"));
        var result = new FinanceDashboardService(repository, clock).dashboard();
        assertEquals(today, result.asOf()); assertEquals(new BigDecimal("800.00"), result.outstanding());
        assertEquals(new BigDecimal("1000.00"), result.dueSoon()); assertEquals(1L, result.dueSoonCount());
        assertEquals(4, result.overdueAging().size());
        assertEquals(new BigDecimal("800.00"), result.overdueAging().get(0).amount());
        assertEquals(clock.instant(), result.generatedAt().toInstant());
        assertEquals(1, result.recentFees().size()); assertTrue(result.upcomingDues().isEmpty());
        verify(repository).upcoming(today, today.plusDays(7), Fee.Status.WAIVED, PageRequest.of(0,8));
    }
}
