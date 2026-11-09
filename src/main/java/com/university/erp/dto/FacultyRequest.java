package com.university.erp.dto;

import jakarta.validation.constraints.*;

public record FacultyRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 120) String department,
    @NotBlank @Size(max = 100) String designation,
    @Size(max = 255) String specialization,
    @Size(max = 255) String qualification,
    @Size(max = 10) String dateOfJoining,
    @Size(max = 10) String dateOfBirth,
    @Size(max = 20) String phone,
    @Size(max = 1000) String address,
    Boolean isActive
) {}
