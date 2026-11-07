package com.university.erp.controller.finance;
import com.university.erp.service.finance.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import java.security.Principal;
import java.util.*;
@RestController @RequestMapping("/api/finance/disbursements") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class DisbursementController {
    private final DisbursementService service;
    private final OperatingFinanceService operations;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/vendors") public Page<Map<String,Object>> vendors(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page){return service.vendors(search,status,page);}
    @PostMapping("/vendors") public Map<String,Object> vendor(@Valid @RequestBody DisbursementService.VendorInput input,Principal actor){return service.createVendor(input,actor.getName());}
    @PutMapping("/vendors/{id}/active") public Map<String,Object> active(@PathVariable UUID id,@Valid @RequestBody DisbursementService.Active input){return service.active(id,input.active());}
    @GetMapping("/staff") public Page<Map<String,Object>> staff(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="0") @Min(0) int page){return service.staff(search,page);}
    private String type(String type){if(!Set.of("VENDOR","PAYROLL").contains(type))throw new IllegalArgumentException("Invalid payment type");return type;}
    @GetMapping("/{type}/payments") public OperatingFinanceService.Worklist list(@PathVariable String type,@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page){return type(type).equals("VENDOR")?operations.vendorPayments(search,status,page):operations.payrollPayments(search,status,page);}
    @PostMapping("/VENDOR/payments") public Map<String,Object> invoice(@Valid @RequestBody DisbursementService.Invoice input,Principal actor){return service.invoice(input,actor.getName());}
    @PostMapping("/PAYROLL/payments") public Map<String,Object> payroll(@Valid @RequestBody DisbursementService.Payroll input,Principal actor){return service.payroll(input,actor.getName());}
    @GetMapping("/{type}/payments/{id}") public Map<String,Object> detail(@PathVariable String type,@PathVariable UUID id){return service.detail(id,type(type));}
    @PostMapping("/{type}/payments/{id}/review") public Map<String,Object> review(@PathVariable String type,@PathVariable UUID id,@Valid @RequestBody OperatingFinanceService.Review input,Principal actor){return service.review(id,type(type),input,actor.getName());}
    @PostMapping("/{type}/payments/{id}/settlement") public Map<String,Object> settle(@PathVariable String type,@PathVariable UUID id,@Valid @RequestBody OperatingFinanceService.Settlement input,Principal actor){return service.settle(id,type(type),input,actor.getName());}
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid payroll/vendor request or filter");}
}
