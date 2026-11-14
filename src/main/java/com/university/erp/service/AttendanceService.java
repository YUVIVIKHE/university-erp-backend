package com.university.erp.service;

import com.university.erp.dto.AttendanceRequest;
import com.university.erp.dto.AttendanceResponse;
import com.university.erp.dto.AttendanceSessionSummary;
import com.university.erp.entity.Attendance;
import com.university.erp.entity.Student;
import com.university.erp.repository.AttendanceRepository;
import com.university.erp.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepo;
    private final StudentRepository    studentRepo;

    @Transactional
    public List<AttendanceResponse> markAttendance(AttendanceRequest req) {
        return req.records.stream().map(record -> {
            Student student = studentRepo.findById(record.studentId)
                .orElseThrow(() -> new NoSuchElementException("Student not found: " + record.studentId));

            Attendance a = attendanceRepo
                .findByStudentIdAndSubjectCodeAndDateAndTimeSlot(
                    record.studentId, req.subjectCode, req.date, req.timeSlot)
                .orElseGet(() -> Attendance.builder()
                    .student(student)
                    .subjectCode(req.subjectCode)
                    .subjectName(req.subjectName)
                    .date(req.date)
                    .timeSlot(req.timeSlot)
                    .build());

            a.setStatus(Attendance.Status.valueOf(record.status));
            a.setRemarks(record.remarks);
            a.setMarkedBy(req.markedBy);
            return AttendanceResponse.from(attendanceRepo.save(a));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> getSession(
            String program, Short batchYear, String subjectCode,
            LocalDate date, String timeSlot) {
        return attendanceRepo.findSession(program, batchYear, subjectCode, date, timeSlot)
            .stream().map(AttendanceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceSessionSummary> recentSessions(String program, Short batchYear, int limit) {
        return attendanceRepo.findRecentSessions(program, batchYear, limit)
            .stream().map(row -> new AttendanceSessionSummary(
                ((java.sql.Date) row[0]).toLocalDate(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                toLong(row[4]),
                toLong(row[5]),
                toLong(row[6])
            )).toList();
    }

    public record AttendanceSummary(Long studentId, String rollNumber, String name,
                                    long present, long total, double percentage) {}

    private static long toLong(Object v) {
        if (v instanceof Long   l) return l;
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    @Transactional(readOnly = true)
    public List<AttendanceSummary> getSummaryForBatch(String program, Short batchYear, String subjectCode) {
        return studentRepo.search("", program, true, PageRequest.of(0, 500))
            .stream()
            .filter(s -> batchYear == null || s.getBatchYear().equals(batchYear))
            .map(s -> {
                long present = attendanceRepo.countPresent(s.getId(), subjectCode);
                long total   = attendanceRepo.countTotal(s.getId(), subjectCode);
                double pct   = total == 0 ? 0.0 : (present * 100.0 / total);
                return new AttendanceSummary(s.getId(), s.getRollNumber(),
                    s.getUser().getFullName(), present, total, pct);
            }).toList();
    }
}
