package com.university.erp.service;

import com.university.erp.dto.SubjectRequest;
import com.university.erp.dto.SubjectResponse;
import com.university.erp.entity.Subject;
import com.university.erp.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository repo;

    @Transactional
    public SubjectResponse create(SubjectRequest req) {
        if (repo.existsBySubjectCode(req.subjectCode.toUpperCase()))
            throw new IllegalArgumentException("Subject code already exists: " + req.subjectCode);

        Subject subject = Subject.builder()
            .subjectCode(req.subjectCode.toUpperCase())
            .name(req.name)
            .description(req.description)
            .course(req.course)
            .semester(req.semester)
            .year(req.year)
            .credits(req.credits)
            .type(req.type)
            .facultyName(req.facultyName)
            .status(req.status != null ? Subject.Status.valueOf(req.status) : Subject.Status.ACTIVE)
            .build();

        return SubjectResponse.from(repo.save(subject));
    }

    public Page<SubjectResponse> list(String search, String course, Integer semester, Integer year, String status, int page) {
        Subject.Status st = (status != null && !status.isBlank()) ? Subject.Status.valueOf(status) : null;
        return repo.search(
            search == null ? "" : search,
            course == null ? "" : course,
            semester,
            year,
            st,
            PageRequest.of(page, 20)
        ).map(SubjectResponse::from);
    }

    public SubjectResponse getById(Long id) {
        return repo.findById(id)
            .map(SubjectResponse::from)
            .orElseThrow(() -> new NoSuchElementException("Subject not found: " + id));
    }

    @Transactional
    public SubjectResponse update(Long id, SubjectRequest req) {
        Subject subject = repo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Subject not found: " + id));

        subject.setName(req.name);
        subject.setDescription(req.description);
        subject.setCourse(req.course);
        subject.setSemester(req.semester);
        subject.setYear(req.year);
        subject.setCredits(req.credits);
        subject.setType(req.type);
        subject.setFacultyName(req.facultyName);
        if (req.status != null) subject.setStatus(Subject.Status.valueOf(req.status));

        return SubjectResponse.from(repo.save(subject));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new NoSuchElementException("Subject not found: " + id);
        repo.deleteById(id);
    }
}
