package com.university.erp.controller;

import com.university.erp.dto.FacultyRequest;
import com.university.erp.dto.FacultyResponse;
import com.university.erp.service.FacultyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faculty")
@RequiredArgsConstructor
public class FacultyController {

    private final FacultyService facultyService;

    @GetMapping
    public ResponseEntity<Page<FacultyResponse>> list(
        @RequestParam(defaultValue = "")    String  search,
        @RequestParam(defaultValue = "")    String  department,
        @RequestParam(required = false)     Boolean active,
        @RequestParam(defaultValue = "0")   int     page,
        @RequestParam(defaultValue = "20")  int     size
    ) {
        return ResponseEntity.ok(facultyService.list(search, department, active, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacultyResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(facultyService.getById(id));
    }

    @PostMapping
    public ResponseEntity<FacultyResponse> create(@Valid @RequestBody FacultyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facultyService.create(req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FacultyResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody FacultyRequest req
    ) {
        return ResponseEntity.ok(facultyService.update(id, req));
    }
}
