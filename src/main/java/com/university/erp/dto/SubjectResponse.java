package com.university.erp.dto;

import com.university.erp.entity.Subject;
import java.time.OffsetDateTime;

public record SubjectResponse(
    Long id,
    String subjectCode,
    String name,
    String description,
    String course,
    Integer semester,
    Integer year,
    Integer credits,
    String type,
    String facultyName,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static SubjectResponse from(Subject s) {
        return new SubjectResponse(
            s.getId(), s.getSubjectCode(), s.getName(), s.getDescription(),
            s.getCourse(), s.getSemester(), s.getYear(), s.getCredits(),
            s.getType(), s.getFacultyName(), s.getStatus().name(),
            s.getCreatedAt(), s.getUpdatedAt()
        );
    }
}
