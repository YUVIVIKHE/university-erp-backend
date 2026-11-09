package com.university.erp.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ExamScheduleRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank String examType,
    @NotBlank @Size(max = 100) String program,
    @NotBlank @Size(max = 100) String course,
    @NotNull @Min(1) @Max(12) Short semester,
    @NotBlank @Size(max = 20) String academicYear,
    @NotBlank String examDate,          // "YYYY-MM-DD"
    @NotBlank String startTime,         // "HH:MM"
    @NotBlank String endTime,           // "HH:MM"
    @Size(max = 200) String venue,
    @NotNull @DecimalMin("1") BigDecimal totalMarks,
    @NotNull @DecimalMin("0") BigDecimal passingMarks,
    Long subjectId,
    String status,
    String instructions
) {}
