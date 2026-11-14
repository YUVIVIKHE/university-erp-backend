package com.university.erp.controller.finance;
import com.university.erp.service.finance.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/finance/payroll") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class PayrollSetupController {
 private final PayrollSetupService service;
 @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping("/departments") public Page<Map<String,Object>> departments(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="0") @Min(0) int page){return service.departments(search,page);}
 @GetMapping("/profiles/{user}") public Map<String,Object> profile(@PathVariable @Positive long user){return service.profile(user);}
 @PutMapping("/profiles/{user}") public Map<String,Object> save(@PathVariable @Positive long user,@Valid @RequestBody PayrollSetupService.Profile input,Principal actor){return service.save(user,input,actor.getName());}
 @GetMapping("/salaries") public Page<Map<String,Object>> salaries(@RequestParam(required=false) @Positive Long user,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page){return service.salaries(user,status,page);}
 @PostMapping("/salaries") public Map<String,Object> salary(@Valid @RequestBody PayrollSetupService.Salary input,Principal actor){return service.salary(input,actor.getName());}
 @PostMapping("/salaries/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody OperatingFinanceService.Review input,Principal actor){return service.review(id,input,actor.getName());}
 @GetMapping("/profiles/{user}/preview") public Map<String,Object> preview(@PathVariable @Positive long user,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate month){return service.preview(user,month);}
 @PostMapping("/profiles/{user}/generate") public Map<String,Object> generate(@PathVariable @Positive long user,@Valid @RequestBody PayrollSetupService.Generate input,Principal actor){return service.generate(user,input,actor.getName());}
 @GetMapping("/snapshots/{id}") public Map<String,Object> snapshot(@PathVariable UUID id){return service.snapshot(id);}
 @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
 public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid payroll profile or salary request");}
}
