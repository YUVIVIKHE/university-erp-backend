package com.university.erp.service;

import com.university.erp.dto.CourseRequest;
import com.university.erp.dto.CourseResponse;
import com.university.erp.entity.Course;
import com.university.erp.entity.CourseFeeComponent;
import com.university.erp.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepo;

    @Transactional
    public CourseResponse create(CourseRequest req) {
        if (courseRepo.existsByCourseCode(req.courseCode))
            throw new IllegalArgumentException("Course code already exists: " + req.courseCode);

        Course course = Course.builder()
            .courseCode(req.courseCode.toUpperCase())
            .name(req.name)
            .description(req.description)
            .program(req.program)
            .credits(req.credits != null ? req.credits : 0)
            .durationMonths(req.durationMonths)
            .level(req.level)
            .mode(req.mode)
            .totalSeats(req.totalSeats)
            .availableSeats(req.availableSeats)
            .status(req.status != null ? Course.Status.valueOf(req.status) : Course.Status.ACTIVE)
            .feeComponents(new ArrayList<>())
            .build();

        if (req.feeComponents != null) {
            for (var fc : req.feeComponents) {
                course.getFeeComponents().add(CourseFeeComponent.builder()
                    .course(course)
                    .feeType(fc.feeType)
                    .amount(fc.amount)
                    .frequency(fc.frequency)
                    .remarks(fc.remarks)
                    .build());
            }
        }

        return CourseResponse.from(courseRepo.save(course));
    }

    public Page<CourseResponse> list(String search, String program, String status, int page) {
        Course.Status st = (status != null && !status.isBlank()) ? Course.Status.valueOf(status) : null;
        return courseRepo.search(
            search == null ? "" : search,
            program == null ? "" : program,
            st,
            PageRequest.of(page, 20)
        ).map(CourseResponse::from);
    }

    public CourseResponse getByCourseCode(String courseCode) {
        return courseRepo.findAll().stream()
            .filter(c -> c.getCourseCode().equalsIgnoreCase(courseCode))
            .findFirst()
            .map(CourseResponse::from)
            .orElseThrow(() -> new NoSuchElementException("Course not found: " + courseCode));
    }

    @Transactional
    public CourseResponse update(String courseCode, CourseRequest req) {
        Course course = courseRepo.findAll().stream()
            .filter(c -> c.getCourseCode().equalsIgnoreCase(courseCode))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Course not found: " + courseCode));

        course.setName(req.name);
        course.setDescription(req.description);
        course.setProgram(req.program);
        course.setCredits(req.credits != null ? req.credits : course.getCredits());
        course.setDurationMonths(req.durationMonths);
        course.setLevel(req.level);
        course.setMode(req.mode);
        course.setTotalSeats(req.totalSeats);
        course.setAvailableSeats(req.availableSeats);
        if (req.status != null) course.setStatus(Course.Status.valueOf(req.status));

        course.getFeeComponents().clear();
        if (req.feeComponents != null) {
            for (var fc : req.feeComponents) {
                course.getFeeComponents().add(CourseFeeComponent.builder()
                    .course(course)
                    .feeType(fc.feeType)
                    .amount(fc.amount)
                    .frequency(fc.frequency)
                    .remarks(fc.remarks)
                    .build());
            }
        }

        return CourseResponse.from(courseRepo.save(course));
    }
}
