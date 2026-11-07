package com.university.erp.controller.finance;

import com.university.erp.service.finance.FinanceReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController @RequestMapping("/api/finance/reports") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FinanceReportsController {
    private final FinanceReportsService service;
    private final com.university.erp.service.finance.FinanceReportDetailsService details;
    @GetMapping("/details") public com.university.erp.service.finance.FinanceReportDetailsService.Detail details(
        @RequestParam String section,@RequestParam(defaultValue="program") String group,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
        @RequestParam(defaultValue="0") int page,jakarta.servlet.http.HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");return details.detail(section,group,from,until,page);
    }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Enter valid report start and end dates");}
    @GetMapping public FinanceReportsService.Report report(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
        jakarta.servlet.http.HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        return service.report(from,until);
    }
}
