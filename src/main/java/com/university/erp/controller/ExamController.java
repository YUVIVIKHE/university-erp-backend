package com.university.erp.controller;

import com.university.erp.dto.*;
import com.university.erp.entity.User;
import com.university.erp.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    // ── Schedules ────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<ExamScheduleResponse>> list(
        @RequestParam(defaultValue = "")   String search,
        @RequestParam(defaultValue = "")   String program,
        @RequestParam(required = false)    Short  semester,
        @RequestParam(defaultValue = "")   String examType,
        @RequestParam(defaultValue = "")   String status,
        @RequestParam(defaultValue = "")   String academicYear,
        @RequestParam(defaultValue = "0")  int    page,
        @RequestParam(defaultValue = "20") int    size
    ) {
        return ResponseEntity.ok(examService.listSchedules(search, program, semester, examType, status, academicYear, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamScheduleResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getSchedule(id));
    }

    @PostMapping
    public ResponseEntity<ExamScheduleResponse> create(
        @Valid @RequestBody ExamScheduleRequest req,
        @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examService.createSchedule(req, actor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExamScheduleResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody ExamScheduleRequest req
    ) {
        return ResponseEntity.ok(examService.updateSchedule(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        examService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }

    // ── Results ──────────────────────────────────────────────────────────────

    @GetMapping("/{examId}/results")
    public ResponseEntity<List<ExamResultResponse>> getResults(@PathVariable Long examId) {
        return ResponseEntity.ok(examService.getResultsForExam(examId));
    }

    @PostMapping("/results")
    public ResponseEntity<List<ExamResultResponse>> saveResults(
        @Valid @RequestBody ExamResultRequest req,
        @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(examService.saveResults(req, actor));
    }

    @GetMapping("/results/student/{rollNumber}")
    public ResponseEntity<List<ExamResultResponse>> getStudentResults(@PathVariable String rollNumber) {
        return ResponseEntity.ok(examService.getResultsForStudent(rollNumber));
    }

    @GetMapping("/results/student/{rollNumber}/semester")
    public ResponseEntity<List<ExamResultResponse>> getStudentSemesterResults(
        @PathVariable String rollNumber,
        @RequestParam String program,
        @RequestParam Short  semester
    ) {
        return ResponseEntity.ok(examService.getResultsForStudentSemester(rollNumber, program, semester));
    }
}
