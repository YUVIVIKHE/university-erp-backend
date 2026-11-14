package com.university.erp.dto;

import com.university.erp.entity.ExamResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExamResultResponse(
    Long           id,
    Long           examId,
    String         examCode,
    String         examTitle,
    String         examType,
    String         examDate,
    Long           studentId,
    String         rollNumber,
    String         studentName,
    String         program,
    Short          semester,
    BigDecimal     totalMarks,
    BigDecimal     passingMarks,
    BigDecimal     marksObtained,
    String         grade,
    BigDecimal     gradePoints,
    boolean        isAbsent,
    Boolean        isPass,
    String         remarks,
    String         enteredByName,
    OffsetDateTime enteredAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static ExamResultResponse from(ExamResult r) {
        var exam    = r.getExam();
        var student = r.getStudent();
        return new ExamResultResponse(
            r.getId(),
            exam.getId(),
            exam.getExamCode(),
            exam.getTitle(),
            exam.getExamType().name(),
            exam.getExamDate() != null ? exam.getExamDate().toString() : null,
            student.getId(),
            student.getRollNumber(),
            student.getUser().getFullName(),
            exam.getProgram(),
            exam.getSemester(),
            exam.getTotalMarks(),
            exam.getPassingMarks(),
            r.getMarksObtained(),
            r.getGrade(),
            r.getGradePoints(),
            r.isAbsent(),
            r.getIsPass(),
            r.getRemarks(),
            r.getEnteredBy() != null ? r.getEnteredBy().getFullName() : null,
            r.getEnteredAt(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
