package com.university.erp.controller;

import com.university.erp.dto.SubjectRequest;
import com.university.erp.dto.SubjectResponse;
import com.university.erp.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest req) {
        return ResponseEntity.ok(subjectService.create(req));
    }

    @GetMapping
    public ResponseEntity<Page<SubjectResponse>> list(
        @RequestParam(defaultValue = "")  String  search,
        @RequestParam(defaultValue = "")  String  course,
        @RequestParam(required = false)   Integer semester,
        @RequestParam(required = false)   Integer year,
        @RequestParam(defaultValue = "")  String  status,
        @RequestParam(defaultValue = "0") int     page
    ) {
        return ResponseEntity.ok(subjectService.list(search, course, semester, year, status, page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubjectResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody SubjectRequest req
    ) {
        return ResponseEntity.ok(subjectService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
