package com.university.erp.service;

import com.university.erp.dto.TimetableRequest;
import com.university.erp.dto.TimetableResponse;
import com.university.erp.entity.Timetable;
import com.university.erp.entity.TimetableSlot;
import com.university.erp.repository.TimetableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository repo;

    @Transactional
    public TimetableResponse save(TimetableRequest req) {
        Timetable tt = repo.findByProgramAndCourseAndBatchYearAndSemester(
                req.program, req.course, req.batchYear, req.semester)
            .orElseGet(() -> Timetable.builder()
                .program(req.program)
                .course(req.course)
                .batchYear(req.batchYear)
                .semester(req.semester)
                .status(Timetable.Status.DRAFT)
                .build());

        tt.setAcademicYear(req.academicYear);
        if (req.status != null) tt.setStatus(Timetable.Status.valueOf(req.status));

        tt.getSlots().clear();
        if (req.slots != null) {
            for (var s : req.slots) {
                tt.getSlots().add(TimetableSlot.builder()
                    .timetable(tt)
                    .day(TimetableSlot.DayOfWeek.valueOf(s.day))
                    .startTime(s.startTime)
                    .endTime(s.endTime)
                    .type(TimetableSlot.SlotType.valueOf(s.type))
                    .subjectCode(s.subjectCode)
                    .subjectName(s.subjectName)
                    .facultyName(s.facultyName)
                    .room(s.room)
                    .slotOrder(s.slotOrder)
                    .build());
            }
        }

        return TimetableResponse.from(repo.save(tt));
    }

    @Transactional(readOnly = true)
    public List<TimetableResponse> listByProgram(String program) {
        return repo.findByProgramOrderByBatchYearDescSemesterAsc(program)
            .stream().map(TimetableResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TimetableResponse get(Long id) {
        return repo.findById(id)
            .map(TimetableResponse::from)
            .orElseThrow(() -> new NoSuchElementException("Timetable not found: " + id));
    }

    @Transactional(readOnly = true)
    public TimetableResponse getByBatch(String program, String course, Short batchYear, Short semester) {
        return repo.findByProgramAndCourseAndBatchYearAndSemester(program, course, batchYear, semester)
            .map(TimetableResponse::from)
            .orElseThrow(() -> new NoSuchElementException("Timetable not found"));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
