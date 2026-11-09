package com.university.erp.service;

import com.university.erp.dto.*;
import com.university.erp.entity.*;
import com.university.erp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamScheduleRepository scheduleRepo;
    private final ExamResultRepository   resultRepo;
    private final StudentRepository      studentRepo;
    private final SubjectRepository      subjectRepo;
    private final UserRepository         userRepo;

    // ── Schedules ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ExamScheduleResponse> listSchedules(
            String search, String program, Short semester,
            String examType, String status, String academicYear, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "examDate"));
        return scheduleRepo.search(search, program, semester, examType, status, academicYear, pageable)
                .map(ExamScheduleResponse::from);
    }

    @Transactional(readOnly = true)
    public ExamScheduleResponse getSchedule(Long id) {
        return scheduleRepo.findById(id)
                .map(ExamScheduleResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Exam not found: " + id));
    }

    @Transactional
    public ExamScheduleResponse createSchedule(ExamScheduleRequest req, User actor) {
        ExamSchedule exam = ExamSchedule.builder()
                .examCode(generateExamCode(req))
                .title(req.title())
                .examType(ExamSchedule.ExamType.valueOf(req.examType()))
                .program(req.program())
                .course(req.course())
                .semester(req.semester())
                .academicYear(req.academicYear())
                .examDate(LocalDate.parse(req.examDate()))
                .startTime(LocalTime.parse(req.startTime()))
                .endTime(LocalTime.parse(req.endTime()))
                .venue(req.venue())
                .totalMarks(req.totalMarks())
                .passingMarks(req.passingMarks())
                .status(req.status() != null && !req.status().isBlank()
                        ? ExamSchedule.Status.valueOf(req.status()) : ExamSchedule.Status.SCHEDULED)
                .instructions(req.instructions())
                .createdBy(actor)
                .build();

        if (req.subjectId() != null)
            exam.setSubject(subjectRepo.findById(req.subjectId())
                    .orElseThrow(() -> new NoSuchElementException("Subject not found: " + req.subjectId())));

        return ExamScheduleResponse.from(scheduleRepo.save(exam));
    }

    @Transactional
    public ExamScheduleResponse updateSchedule(Long id, ExamScheduleRequest req) {
        ExamSchedule exam = scheduleRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Exam not found: " + id));

        exam.setTitle(req.title());
        exam.setExamType(ExamSchedule.ExamType.valueOf(req.examType()));
        exam.setProgram(req.program());
        exam.setCourse(req.course());
        exam.setSemester(req.semester());
        exam.setAcademicYear(req.academicYear());
        exam.setExamDate(LocalDate.parse(req.examDate()));
        exam.setStartTime(LocalTime.parse(req.startTime()));
        exam.setEndTime(LocalTime.parse(req.endTime()));
        exam.setVenue(req.venue());
        exam.setTotalMarks(req.totalMarks());
        exam.setPassingMarks(req.passingMarks());
        exam.setInstructions(req.instructions());
        if (req.status() != null && !req.status().isBlank())
            exam.setStatus(ExamSchedule.Status.valueOf(req.status()));
        if (req.subjectId() != null)
            exam.setSubject(subjectRepo.findById(req.subjectId()).orElse(null));
        else
            exam.setSubject(null);

        return ExamScheduleResponse.from(scheduleRepo.save(exam));
    }

    @Transactional
    public void deleteSchedule(Long id) {
        if (!scheduleRepo.existsById(id))
            throw new NoSuchElementException("Exam not found: " + id);
        scheduleRepo.deleteById(id);
    }

    // ── Results ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamResultResponse> getResultsForExam(Long examId) {
        return resultRepo.findByExamId(examId).stream()
                .map(ExamResultResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamResultResponse> getResultsForStudent(String rollNumber) {
        return resultRepo.findByStudentRollNumber(rollNumber).stream()
                .map(ExamResultResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamResultResponse> getResultsForStudentSemester(String rollNumber, String program, Short semester) {
        return resultRepo.findByStudentAndSemester(rollNumber, program, semester).stream()
                .map(ExamResultResponse::from).toList();
    }

    @Transactional
    public List<ExamResultResponse> saveResults(ExamResultRequest req, User actor) {
        ExamSchedule exam = scheduleRepo.findById(req.examId())
                .orElseThrow(() -> new NoSuchElementException("Exam not found: " + req.examId()));

        List<ExamResult> saved = new ArrayList<>();

        for (ExamResultRequest.ResultEntry entry : req.results()) {
            Student student = studentRepo.findById(entry.studentId())
                    .orElseThrow(() -> new NoSuchElementException("Student not found: " + entry.studentId()));

            ExamResult result = resultRepo.findByExamIdAndStudentId(exam.getId(), student.getId())
                    .orElseGet(() -> ExamResult.builder()
                            .exam(exam)
                            .student(student)
                            .build());

            if (entry.isAbsent()) {
                result.setAbsent(true);
                result.setMarksObtained(null);
                result.setGrade("AB");
                result.setGradePoints(BigDecimal.ZERO);
                result.setIsPass(false);
            } else {
                BigDecimal marks = entry.marksObtained() != null ? entry.marksObtained() : BigDecimal.ZERO;
                result.setAbsent(false);
                result.setMarksObtained(marks);
                result.setIsPass(marks.compareTo(exam.getPassingMarks()) >= 0);
                String grade = computeGrade(marks, exam.getTotalMarks());
                result.setGrade(grade);
                result.setGradePoints(computeGradePoints(grade));
            }

            result.setRemarks(entry.remarks());
            result.setEnteredBy(actor);
            result.setEnteredAt(OffsetDateTime.now());
            saved.add(resultRepo.save(result));
        }

        return saved.stream().map(ExamResultResponse::from).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateExamCode(ExamScheduleRequest req) {
        String prefix = req.examType().substring(0, Math.min(3, req.examType().length())).toUpperCase();
        String year   = req.academicYear().replace("-", "").substring(0, Math.min(4, req.academicYear().replace("-", "").length()));
        long   seq    = scheduleRepo.count() + 1;
        String candidate;
        do {
            candidate = prefix + year + String.format("%04d", seq++);
        } while (scheduleRepo.existsByExamCode(candidate));
        return candidate;
    }

    private String computeGrade(BigDecimal marks, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return "F";
        BigDecimal pct = marks.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
        int p = pct.intValue();
        if (p >= 90) return "O";
        if (p >= 80) return "A+";
        if (p >= 70) return "A";
        if (p >= 60) return "B+";
        if (p >= 50) return "B";
        if (p >= 40) return "C";
        return "F";
    }

    private BigDecimal computeGradePoints(String grade) {
        return switch (grade) {
            case "O"  -> new BigDecimal("10");
            case "A+" -> new BigDecimal("9");
            case "A"  -> new BigDecimal("8");
            case "B+" -> new BigDecimal("7");
            case "B"  -> new BigDecimal("6");
            case "C"  -> new BigDecimal("5");
            default   -> BigDecimal.ZERO;
        };
    }
}
