package com.university.erp.controller;

import com.university.erp.dto.*;
import com.university.erp.entity.Student;
import com.university.erp.entity.User;
import com.university.erp.repository.AttendanceRepository;
import com.university.erp.repository.StudentRepository;
import com.university.erp.service.ExamService;
import com.university.erp.service.FeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentPortalController {

    private final StudentRepository       studentRepo;
    private final AttendanceRepository    attendanceRepo;
    private final ExamService             examService;
    private final FeeService              feeService;

    private Student resolve(User user) {
        return studentRepo.findByUserId(user.getId())
            .orElseThrow(() -> new NoSuchElementException("No student profile for this account."));
    }

    /** Own profile */
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(StudentResponse.from(resolve(user)));
    }

    /** Exam results */
    @GetMapping("/results")
    public ResponseEntity<List<ExamResultResponse>> results(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        return ResponseEntity.ok(examService.getResultsForStudent(s.getRollNumber()));
    }

    /** Results for a specific semester */
    @GetMapping("/results/semester")
    public ResponseEntity<List<ExamResultResponse>> resultsBySemester(
        @AuthenticationPrincipal User user,
        @RequestParam Short semester
    ) {
        Student s = resolve(user);
        return ResponseEntity.ok(
            examService.getResultsForStudentSemester(s.getRollNumber(), s.getProgram(), semester)
        );
    }

    /** Attendance summary per subject */
    @GetMapping("/attendance")
    public ResponseEntity<List<Map<String, Object>>> attendance(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        List<Object[]> raw = attendanceRepo.getSubjectSummaryRaw(s.getId());
        List<Map<String, Object>> rows = raw.stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("subjectCode", r[0]);
            m.put("subjectName", r[1]);
            m.put("total",       r[2]);
            m.put("present",     r[3]);
            m.put("absent",      r[4]);
            return m;
        }).toList();
        return ResponseEntity.ok(rows);
    }

    /** Fee records */
    @GetMapping("/fees")
    public ResponseEntity<List<FeeResponse>> fees(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(feeService.getFeesForStudent(user.getUsername()));
    }
}
