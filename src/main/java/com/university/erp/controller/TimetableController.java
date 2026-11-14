package com.university.erp.controller;

import com.university.erp.dto.TimetableRequest;
import com.university.erp.dto.TimetableResponse;
import com.university.erp.service.TimetableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService service;

    @PostMapping
    public TimetableResponse save(@Valid @RequestBody TimetableRequest req) {
        return service.save(req);
    }

    @GetMapping
    public List<TimetableResponse> listByProgram(@RequestParam String program) {
        return service.listByProgram(program);
    }

    @GetMapping("/{id}")
    public TimetableResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/batch")
    public TimetableResponse getByBatch(
            @RequestParam String program,
            @RequestParam String course,
            @RequestParam Short batchYear,
            @RequestParam Short semester) {
        return service.getByBatch(program, course, batchYear, semester);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
