package com.university.erp.controller.finance;
import com.university.erp.service.finance.FinanceActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Page;
@RestController @RequestMapping("/api/finance/activity") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FinanceActivityController {
 private final FinanceActivityService service;
 @GetMapping public Page<Map<String,Object>> list(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
 @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,@RequestParam(defaultValue="") String search,
 @RequestParam(defaultValue="") String kind,@RequestParam(defaultValue="") String outcome,@RequestParam(defaultValue="0") int page,
 jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.list(from,until,search,kind,outcome,page);}
 @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
 public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Enter valid activity filters and dates");}
}
