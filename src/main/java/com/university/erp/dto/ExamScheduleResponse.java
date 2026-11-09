package com.university.erp.dto;

import com.university.erp.entity.ExamSchedule;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExamScheduleResponse(
    Long           id,
    String         examCode,
    String         title,
    String         examType,
    String         program,
    String         course,
    Short          semester,
    String         academicYear,
    String         examDate,
    String         startTime,
    String         endTime,
    String         venue,
    BigDecimal     totalMarks,
    BigDecimal     passingMarks,
    Long           subjectId,
    String         subjectName,
    String         subjectCode,
    String         status,
    String         instructions,
    String         createdByName,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static ExamScheduleResponse from(ExamSchedule e) {
        return new ExamScheduleResponse(
            e.getId(),
            e.getExamCode(),
            e.getTitle(),
            e.getExamType().name(),
            e.getProgram(),
            e.getCourse(),
            e.getSemester(),
            e.getAcademicYear(),
            e.getExamDate() != null    ? e.getExamDate().toString()    : null,
            e.getStartTime() != null   ? e.getStartTime().toString()   : null,
            e.getEndTime() != null     ? e.getEndTime().toString()     : null,
            e.getVenue(),
            e.getTotalMarks(),
            e.getPassingMarks(),
            e.getSubject() != null     ? e.getSubject().getId()        : null,
            e.getSubject() != null     ? e.getSubject().getName()      : null,
            e.getSubject() != null     ? e.getSubject().getSubjectCode(): null,
            e.getStatus().name(),
            e.getInstructions(),
            e.getCreatedBy() != null   ? e.getCreatedBy().getFullName(): null,
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
