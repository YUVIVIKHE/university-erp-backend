package com.university.erp.dto;

import com.university.erp.entity.Attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AttendanceResponse(
    Long id,
    Long studentId,
    String rollNumber,
    String studentName,
    String subjectCode,
    String subjectName,
    LocalDate date,
    String timeSlot,
    String status,
    String remarks,
    String markedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static AttendanceResponse from(Attendance a) {
        return new AttendanceResponse(
            a.getId(),
            a.getStudent().getId(),
            a.getStudent().getRollNumber(),
            a.getStudent().getUser().getFullName(),
            a.getSubjectCode(),
            a.getSubjectName(),
            a.getDate(),
            a.getTimeSlot(),
            a.getStatus().name(),
            a.getRemarks(),
            a.getMarkedBy(),
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }
}
