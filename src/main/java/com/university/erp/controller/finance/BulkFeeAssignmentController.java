package com.university.erp.controller.finance;
import com.university.erp.service.finance.BulkFeeAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api/finance/fee-assignments") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class BulkFeeAssignmentController {
    private final BulkFeeAssignmentService service;
    @GetMapping("/programs") public List<String> programs(@RequestParam(defaultValue="") @Size(max=100) String search){return service.programs(search);}
    @PostMapping("/structures/{id}/preview") public BulkFeeAssignmentService.Run preview(@PathVariable long id,@Valid @RequestBody BulkFeeAssignmentService.Selection selection,Principal actor){return service.preview(id,selection,actor.getName());}
    @GetMapping("/structures/{id}/history") public List<BulkFeeAssignmentService.Run> history(@PathVariable long id,Principal actor){return service.history(id,actor.getName());}
    @GetMapping("/{id}") public BulkFeeAssignmentService.Run get(@PathVariable UUID id,Principal actor){return service.get(id,actor.getName());}
    @PostMapping("/{id}/process") public BulkFeeAssignmentService.Run process(@PathVariable UUID id,Principal actor){return service.process(id,actor.getName());}
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid filter parameter");}
}
