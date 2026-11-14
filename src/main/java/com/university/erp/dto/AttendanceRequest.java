package com.university.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public class AttendanceRequest {

    @NotNull  public LocalDate date;
    @NotBlank public String timeSlot;
    @NotBlank public String subjectCode;
    @NotBlank public String subjectName;
    public String markedBy;

    @NotEmpty @Valid
    public List<StudentRecord> records;

    public static class StudentRecord {
        @NotNull  public Long studentId;
        @NotBlank public String status;
        public String remarks;
    }
}
