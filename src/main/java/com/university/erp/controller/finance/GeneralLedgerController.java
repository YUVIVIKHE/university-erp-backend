package com.university.erp.controller.finance;
import com.university.erp.service.finance.GeneralLedgerService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/finance/ledger") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class GeneralLedgerController {
    private final GeneralLedgerService service;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/journals/{id}") public Map<String,Object> journal(@PathVariable @Size(max=100) String id){return service.journal(id);}
    @GetMapping("/accounts") public List<Map<String,Object>> accounts(){return service.accounts();}
    @GetMapping("/journals") public Page<Map<String,Object>> journals(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="") String source,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,@RequestParam(defaultValue="0") @Min(0) int page){return service.journals(search,source,from,until,page);}
    @GetMapping("/trial-balance") public GeneralLedgerService.Trial trial(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until){return service.trial(from,until);}
    @GetMapping("/accounts/{code}") public GeneralLedgerService.Statement statement(@PathVariable @Pattern(regexp="[0-9]{4}") String code,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,@RequestParam(defaultValue="0") @Min(0) int page){return service.statement(code,from,until,page);}
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid ledger filter or account");}
}
