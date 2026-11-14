package com.university.erp.dto;

import com.university.erp.entity.Timetable;
import com.university.erp.entity.TimetableSlot;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record TimetableResponse(
    Long id,
    String program,
    String course,
    Short batchYear,
    Short semester,
    String academicYear,
    String status,
    List<SlotResponse> slots,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public record SlotResponse(
        Long id,
        String day,
        LocalTime startTime,
        LocalTime endTime,
        String type,
        String subjectCode,
        String subjectName,
        String facultyName,
        String room,
        Integer slotOrder
    ) {
        public static SlotResponse from(TimetableSlot s) {
            return new SlotResponse(s.getId(), s.getDay().name(), s.getStartTime(), s.getEndTime(),
                s.getType().name(), s.getSubjectCode(), s.getSubjectName(),
                s.getFacultyName(), s.getRoom(), s.getSlotOrder());
        }
    }

    public static TimetableResponse from(Timetable t) {
        return new TimetableResponse(t.getId(), t.getProgram(), t.getCourse(),
            t.getBatchYear(), t.getSemester(), t.getAcademicYear(),
            t.getStatus().name(),
            t.getSlots().stream().map(SlotResponse::from).toList(),
            t.getCreatedAt(), t.getUpdatedAt());
    }
}
