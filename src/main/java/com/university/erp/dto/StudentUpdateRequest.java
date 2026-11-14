package com.university.erp.dto;

import jakarta.validation.constraints.*;

public record StudentUpdateRequest(
    @Size(max = 100) String program,
    @Min(1) @Max(100) Short semester,
    @Min(2000) @Max(2200) Short batchYear,
    @Size(max = 10) String dateOfBirth,
    @Size(max = 20) String phone,
    @Size(max = 1000) String address,
    @Size(max = 255) String guardianName,
    @Size(max = 20) String guardianPhone,
    Boolean isActive
) {}
