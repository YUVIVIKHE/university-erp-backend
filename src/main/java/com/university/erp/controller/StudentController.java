package com.university.erp.controller;

import com.university.erp.dto.StudentResponse;
import com.university.erp.dto.StudentUpdateRequest;
import com.university.erp.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public ResponseEntity<Page<StudentResponse>> list(
        @RequestParam(defaultValue = "")    String  search,
        @RequestParam(defaultValue = "")    String  program,
        @RequestParam(required = false)     Boolean active,
        @RequestParam(defaultValue = "0")   int     page,
        @RequestParam(defaultValue = "20")  int     size
    ) {
        return ResponseEntity.ok(studentService.list(search, program, active, page, size));
    }

    @GetMapping("/{rollNumber}")
    public ResponseEntity<StudentResponse> getOne(@PathVariable String rollNumber) {
        return ResponseEntity.ok(studentService.getByRollNumber(rollNumber));
    }

    @PatchMapping("/{rollNumber}")
    public ResponseEntity<StudentResponse> update(
        @PathVariable String rollNumber,
        @Valid @RequestBody StudentUpdateRequest req
    ) {
        return ResponseEntity.ok(studentService.update(rollNumber, req));
    }
}
