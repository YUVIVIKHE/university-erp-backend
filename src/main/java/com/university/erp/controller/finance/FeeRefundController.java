package com.university.erp.controller.finance;
import com.university.erp.service.finance.FeeRefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import java.security.Principal;
import java.util.*;
@RestController @RequestMapping("/api/finance/refunds") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FeeRefundController {
    private final FeeRefundService service;
    @GetMapping public Page<Map<String,Object>> list(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") @Min(0) int page,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.list(search,status,page);}
    @GetMapping("/summary") public List<Map<String,Object>> summary(@RequestParam(defaultValue="") @Size(max=100) String search,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.summary(search);}
    @GetMapping("/receipts/{id}") public Map<String,Object> receipt(@PathVariable UUID id,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.receipt(id);}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID id,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.detail(id);}
    @PostMapping public Map<String,Object> create(@Valid @RequestBody FeeRefundService.Input input,Principal actor){return service.create(input,actor.getName());}
    @PutMapping("/{id}") public Map<String,Object> edit(@PathVariable UUID id,@Valid @RequestBody FeeRefundService.Edit input,Principal actor){return service.edit(id,input,actor.getName());}
    @PostMapping("/{id}/actions") public Map<String,Object> action(@PathVariable UUID id,@Valid @RequestBody FeeRefundService.Action input,Principal actor){return service.action(id,input,actor.getName());}
    @PostMapping("/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody FeeRefundService.Review input,Principal actor){return service.review(id,input,actor.getName());}
    @PostMapping("/{id}/payout") public Map<String,Object> payout(@PathVariable UUID id,@Valid @RequestBody FeeRefundService.Payout input,Principal actor){return service.payout(id,input,actor.getName());}
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid refund request or filter");}
}
