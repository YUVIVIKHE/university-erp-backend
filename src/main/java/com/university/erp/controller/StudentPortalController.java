package com.university.erp.controller;

import com.university.erp.dto.*;
import com.university.erp.entity.Attendance;
import com.university.erp.entity.Student;
import com.university.erp.entity.User;
import com.university.erp.repository.AttendanceRepository;
import com.university.erp.repository.StudentRepository;
import com.university.erp.repository.TimetableRepository;
import com.university.erp.service.ExamService;
import com.university.erp.service.FeeService;
import com.university.erp.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final TimetableRepository     timetableRepo;
    private final ExamService             examService;
    private final FeeService              feeService;
    private final SubjectService          subjectService;

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

    /** Subjects for the student's program (all semesters) */
    @GetMapping("/subjects")
    public ResponseEntity<Page<SubjectResponse>> subjects(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "0") int page
    ) {
        Student s = resolve(user);
        return ResponseEntity.ok(subjectService.list("", s.getProgram(), null, null, "ACTIVE", page));
    }

    /** Weekly timetable for student's program/batch/semester */
    @GetMapping("/timetable")
    public ResponseEntity<?> timetable(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        return timetableRepo
            .findByProgramAndCourseAndBatchYearAndSemester(s.getProgram(), s.getProgram(), s.getBatchYear(), s.getSemester())
            .or(() -> timetableRepo.findByProgramOrderByBatchYearDescSemesterAsc(s.getProgram()).stream()
                .filter(t -> t.getSemester().equals(s.getSemester()))
                .findFirst())
            .map(t -> ResponseEntity.ok(TimetableResponse.from(t)))
            .orElse(ResponseEntity.ok(null));
    }

    /** All attendance records for the student (session-level detail) */
    @GetMapping("/attendance/detail")
    public ResponseEntity<List<Map<String, Object>>> attendanceDetail(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        List<Map<String, Object>> rows = attendanceRepo.findAllByStudentId(s.getId()).stream().map(a -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",          a.getId());
            m.put("subjectCode", a.getSubjectCode());
            m.put("subjectName", a.getSubjectName());
            m.put("date",        a.getDate().toString());
            m.put("timeSlot",    a.getTimeSlot());
            m.put("status",      a.getStatus().name());
            m.put("remarks",     a.getRemarks());
            return m;
        }).toList();
        return ResponseEntity.ok(rows);
    }

    /** Update own contact/guardian info */
    @PatchMapping("/profile")
    public ResponseEntity<StudentResponse> updateProfile(
        @AuthenticationPrincipal User user,
        @RequestBody Map<String, String> body
    ) {
        Student s = resolve(user);
        if (body.containsKey("phone"))         s.setPhone(body.get("phone"));
        if (body.containsKey("address"))       s.setAddress(body.get("address"));
        if (body.containsKey("guardianName"))  s.setGuardianName(body.get("guardianName"));
        if (body.containsKey("guardianPhone")) s.setGuardianPhone(body.get("guardianPhone"));
        return ResponseEntity.ok(StudentResponse.from(studentRepo.save(s)));
    }
}
