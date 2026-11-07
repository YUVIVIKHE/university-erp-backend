package com.university.erp.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record FinanceDashboard(
    LocalDate asOf, BigDecimal totalBilled, BigDecimal totalCollected, BigDecimal totalConcessions, BigDecimal totalRefunded,
    BigDecimal outstanding, BigDecimal overdueAmount, BigDecimal waivedBalance,
    long totalRecords, long paid, long pending, long partial, long overdue, long waived,
    List<FeeItem> recentFees, List<FeeItem> upcomingDues,
    BigDecimal dueSoon, long dueSoonCount, List<AgingBucket> overdueAging, OffsetDateTime generatedAt
) {
    public record AgingBucket(String label, BigDecimal amount) {}
    public record FeeItem(String feeId, String admissionId, String studentName, String program,
        String feeType, BigDecimal amount, BigDecimal amountPaid, BigDecimal concessionAmount, BigDecimal balance,
        LocalDate dueDate, String status, OffsetDateTime createdAt) {}
}
