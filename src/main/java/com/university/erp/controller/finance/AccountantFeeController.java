package com.university.erp.controller.finance;

import com.university.erp.service.finance.AccountantFeeService;
import com.university.erp.dto.finance.FinanceDashboard.FeeItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/fees")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
public class AccountantFeeController {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<com.university.erp.dto.ApiError> invalid(org.springframework.web.server.ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode())
            .body(com.university.erp.dto.ApiError.of(e.getStatusCode().value(), e.getReason()));
    }
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public org.springframework.http.ResponseEntity<com.university.erp.dto.ApiError> invalidQuery(jakarta.validation.ConstraintViolationException e) {
        return org.springframework.http.ResponseEntity.badRequest()
            .body(com.university.erp.dto.ApiError.of(400, "Invalid search or page parameter"));
    }
    private final AccountantFeeService service;
    private final com.university.erp.service.finance.StudentFeeAccountService accounts;
    @GetMapping("/accounts") public Page<com.university.erp.service.finance.StudentFeeAccountService.Summary> accounts(
        @RequestParam(defaultValue="") @Size(max=100) String search,
        @RequestParam(defaultValue="") @Size(max=100) String program,
        @RequestParam(defaultValue="0") @Min(0) @Max(100) int semester,
        @RequestParam(defaultValue="0") @Min(0) @Max(9999) int batch,
        @RequestParam(defaultValue="0") @Min(0) int page) {
        return accounts.directory(search,program,semester,batch,page);
    }
    @GetMapping("/accounts/{id}") public com.university.erp.service.finance.StudentFeeAccountService.AccountDetail account(
        @PathVariable String id, @RequestParam(defaultValue="0") @Min(0) int page) { return accounts.account(id,page); }
    @GetMapping public Page<FeeItem> list(@RequestParam(defaultValue="") @Size(max=100) String search,
        @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="0") @Min(0) int page) {
        return service.list(search, status, page);
    }
    @GetMapping("/admissions") public Page<AccountantFeeService.AdmissionOption> admissions(
        @RequestParam(defaultValue="") @Size(max=100) String search, @RequestParam(defaultValue="0") @Min(0) int page) {
        return service.admissions(search, page);
    }
    @GetMapping("/{id}") public AccountantFeeService.Detail detail(@PathVariable String id) { return service.detail(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FeeItem assign(@Valid @RequestBody AccountantFeeService.AssignRequest request) { return service.assign(request); }
}
