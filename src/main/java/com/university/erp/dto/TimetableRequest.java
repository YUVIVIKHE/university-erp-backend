package com.university.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalTime;
import java.util.List;

public class TimetableRequest {

    @NotBlank public String program;
    @NotBlank public String course;
    @NotNull  public Short  batchYear;
    @NotNull  public Short  semester;
    public String academicYear;
    public String status;

    @Valid
    public List<SlotRequest> slots;

    public static class SlotRequest {
        @NotBlank public String day;
        @NotNull  public LocalTime startTime;
        @NotNull  public LocalTime endTime;
        @NotBlank public String type;
        public String subjectCode;
        public String subjectName;
        public String facultyName;
        public String room;
        public Integer slotOrder;
    }
}
