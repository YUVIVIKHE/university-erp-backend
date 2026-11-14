package com.university.erp.dto;

import java.time.LocalDate;

public record AttendanceSessionSummary(
    LocalDate date,
    String subjectCode,
    String subjectName,
    String timeSlot,
    long presentCount,
    long absentCount,
    long totalCount
) {}
