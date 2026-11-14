package com.university.erp.dto;

import jakarta.validation.constraints.*;

public class SubjectRequest {
    @NotBlank public String subjectCode;
    @NotBlank public String name;
    public String description;
    @NotBlank public String course;
    @NotNull @Min(1) @Max(12) public Integer semester;
    @NotNull @Min(2000) public Integer year;
    @NotNull @Min(1) public Integer credits;
    @NotBlank public String type;
    public String facultyName;
    public String status;
}
