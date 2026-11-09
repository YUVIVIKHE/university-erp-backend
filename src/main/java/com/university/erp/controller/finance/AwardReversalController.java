package com.university.erp.controller.finance;
import com.university.erp.service.finance.AwardReversalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import java.util.*;
import java.security.Principal;
@RestController @RequestMapping("/api/finance/award-reversals") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class AwardReversalController {
 private final AwardReversalService service;
 @GetMapping public Page<Map<String,Object>> list(@RequestParam(defaultValue="") @Size(max=100) String search,@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="0") @Min(0) int page,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.list(search,status,page);}
 @PostMapping("/applications/{id}") public Map<String,Object> request(@PathVariable UUID id,@Valid @RequestBody AwardReversalService.Request input,Principal actor){return service.request(id,input,actor.getName());}
 @PostMapping("/{id}/review") public Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody AwardReversalService.Review input,Principal actor){return service.review(id,input,actor.getName());}
 @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
 public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid reversal filter or request");}
}
