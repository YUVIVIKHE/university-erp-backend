package com.university.erp.controller.finance;
import com.university.erp.service.finance.FeeCollectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.security.Principal;
import java.util.UUID;
@RestController @RequestMapping("/api/finance/collections") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FeeCollectionController {
    private final FeeCollectionService service;
    @PostMapping public FeeCollectionService.Receipt collect(@Valid @RequestBody FeeCollectionService.PaymentInput input,Principal actor){return service.collect(input,actor.getName());}
    @GetMapping public Page<FeeCollectionService.Payment> history(@RequestParam(defaultValue="") @Size(max=100) String search,
        @RequestParam(defaultValue="") @Size(max=30) String admissionId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
        @RequestParam(defaultValue="0") @Min(0) int page){return service.history(search,admissionId,from,until,page);}
    @GetMapping("/charges") public Page<FeeCollectionService.Charge> charges(@RequestParam @NotBlank @Size(max=30) String admissionId,@RequestParam(defaultValue="0") @Min(0) int page){return service.charges(admissionId,page);}
    @GetMapping("/receipts/{id}") public FeeCollectionService.Receipt receipt(@PathVariable UUID id,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.receipt(id);}
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid filter or page parameter");}
}
