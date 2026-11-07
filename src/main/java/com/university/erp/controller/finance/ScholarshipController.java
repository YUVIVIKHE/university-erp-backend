package com.university.erp.controller.finance;
import com.university.erp.service.finance.ScholarshipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import java.security.Principal;
import java.util.*;
@RestController @RequestMapping("/api/finance/scholarships") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class ScholarshipController {
    private final ScholarshipService service;
    @GetMapping public Page<Map<String,Object>> list(@RequestParam(defaultValue="") @Size(max=100) String search,
        @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page,
        jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.list(search,status,page);}
    @PostMapping public Map<String,Object> create(@Valid @RequestBody ScholarshipService.Input input,Principal actor){return service.create(input,actor.getName());}
    @PostMapping("/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody ScholarshipService.Review input,Principal actor){return service.review(id,input,actor.getName());}
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid scholarship application or filter");}
}
