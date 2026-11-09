package com.university.erp.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ExamResultRequest(
    @NotNull Long examId,
    @NotNull List<ResultEntry> results
) {
    public record ResultEntry(
        @NotNull Long studentId,
        BigDecimal marksObtained,
        boolean isAbsent,
        String remarks
    ) {}
}
