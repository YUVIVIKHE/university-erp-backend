package com.university.erp.controller;

import com.university.erp.dto.AttendanceRequest;
import com.university.erp.dto.AttendanceResponse;
import com.university.erp.dto.AttendanceSessionSummary;
import com.university.erp.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    public List<AttendanceResponse> mark(@Valid @RequestBody AttendanceRequest req) {
        return attendanceService.markAttendance(req);
    }

    @GetMapping("/session")
    public List<AttendanceResponse> session(
            @RequestParam String program,
            @RequestParam(required = false) Short batchYear,
            @RequestParam String subjectCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String timeSlot) {
        return attendanceService.getSession(program, batchYear, subjectCode, date, timeSlot);
    }

    @GetMapping("/recent-sessions")
    public List<AttendanceSessionSummary> recentSessions(
            @RequestParam String program,
            @RequestParam(required = false) Short batchYear,
            @RequestParam(defaultValue = "30") int limit) {
        return attendanceService.recentSessions(program, batchYear, limit);
    }

    @GetMapping("/summary")
    public List<AttendanceService.AttendanceSummary> summary(
            @RequestParam String program,
            @RequestParam(required = false) Short batchYear,
            @RequestParam String subjectCode) {
        return attendanceService.getSummaryForBatch(program, batchYear, subjectCode);
    }
}
