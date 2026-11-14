package com.university.erp.dto;

import com.university.erp.entity.Faculty;

import java.time.OffsetDateTime;

public record FacultyResponse(
    Long           id,
    Long           userId,
    String         employeeId,
    String         fullName,
    String         email,
    String         department,
    String         designation,
    String         specialization,
    String         qualification,
    String         dateOfJoining,
    String         dateOfBirth,
    String         phone,
    String         address,
    boolean        isActive,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    /** Only populated on initial creation — null on all other responses */
    String         tempPassword
) {
    public static FacultyResponse from(Faculty f) {
        return from(f, null);
    }

    public static FacultyResponse from(Faculty f, String tempPassword) {
        return new FacultyResponse(
            f.getId(),
            f.getUser().getId(),
            f.getEmployeeId(),
            f.getUser().getFullName(),
            f.getUser().getEmail(),
            f.getDepartment(),
            f.getDesignation(),
            f.getSpecialization(),
            f.getQualification(),
            f.getDateOfJoining() != null ? f.getDateOfJoining().toString() : null,
            f.getDateOfBirth()   != null ? f.getDateOfBirth().toString()   : null,
            f.getPhone(),
            f.getAddress(),
            f.isActive(),
            f.getCreatedAt(),
            f.getUpdatedAt(),
            tempPassword
        );
    }
}
