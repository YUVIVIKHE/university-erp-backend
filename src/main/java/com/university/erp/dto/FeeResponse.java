package com.university.erp.dto;

import com.university.erp.entity.Fee;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FeeResponse(
    Long id,
    String feeId,
    String admissionId,
    String studentName,
    String program,
    String feeType,
    BigDecimal amount,
    BigDecimal amountPaid,
    BigDecimal concessionAmount,
    BigDecimal balance,
    LocalDate dueDate,
    LocalDate paidDate,
    String status,
    String remarks,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static FeeResponse from(Fee f) {
        var a = f.getAdmission();
        return new FeeResponse(
            f.getId(),
            f.getFeeId(),
            a.getAdmissionId(),
            a.getFirstName() + " " + a.getLastName(),
            a.getProgram(),
            f.getFeeType(),
            f.getAmount(),
            f.getAmountPaid(),
            f.getConcessionAmount(),
            f.getStatus()==Fee.Status.WAIVED ? BigDecimal.ZERO : f.netAmount().subtract(f.getAmountPaid()).max(BigDecimal.ZERO),
            f.getDueDate(),
            f.getPaidDate(),
            f.getStatus().name(),
            f.getRemarks(),
            f.getCreatedAt(),
            f.getUpdatedAt()
        );
    }

    public record Stats(
        long total,
        long paid,
        long pending,
        long overdue,
        java.math.BigDecimal totalAmount,
        java.math.BigDecimal totalCollected
    ) {}
}
