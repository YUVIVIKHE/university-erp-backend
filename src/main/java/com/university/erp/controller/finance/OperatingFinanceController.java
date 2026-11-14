package com.university.erp.controller.finance;
import com.university.erp.service.finance.OperatingFinanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.security.Principal;
import java.util.*;
@RestController @RequestMapping("/api/finance/operations") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class OperatingFinanceController {
    private final OperatingFinanceService service;
    @GetMapping public OperatingFinanceService.Worklist list(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="") String direction,@RequestParam(defaultValue="") String status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
        @RequestParam(defaultValue="0") @Min(0) int page,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.list(search,direction,status,from,until,page);}
    @GetMapping("/categories") public Map<String,List<String>> categories(){return OperatingFinanceService.CATEGORIES;}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID id,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.detail(id);}
    @PostMapping public Map<String,Object> create(@Valid @RequestBody OperatingFinanceService.Input input,Principal actor){return service.create(input,actor.getName());}
    @PostMapping("/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody OperatingFinanceService.Review input,Principal actor){return service.review(id,input,actor.getName());}
    @PostMapping("/{id}/settlement") public Map<String,Object> settle(@PathVariable UUID id,@Valid @RequestBody OperatingFinanceService.Settlement input,Principal actor){return service.settle(id,input,actor.getName());}
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid income/expense request or filter");}
}
