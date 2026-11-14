package com.university.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public class CourseRequest {

    @NotBlank public String courseCode;
    @NotBlank public String name;
    public String description;
    @NotBlank public String program;
    public Integer credits;
    @NotNull @Min(1) public Integer durationMonths;
    @NotBlank public String level;
    @NotBlank public String mode;
    @NotNull @Min(1) public Integer totalSeats;
    @NotNull @Min(0) public Integer availableSeats;
    public String status;

    @Valid
    public List<FeeComponentRequest> feeComponents;

    public static class FeeComponentRequest {
        @NotBlank public String feeType;
        @NotNull @DecimalMin("0") public BigDecimal amount;
        @NotBlank public String frequency;
        public String remarks;
    }
}
