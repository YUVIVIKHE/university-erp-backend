package com.university.erp.controller.finance;
import com.university.erp.service.finance.FeeFollowupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import java.security.Principal;
@RestController @RequestMapping("/api/finance/defaulters") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class FeeFollowupController {
    private final FeeFollowupService service;
    @GetMapping public FeeFollowupService.Worklist list(@RequestParam(defaultValue="") @Size(max=100) String search,
        @RequestParam(defaultValue="") @Size(max=100) String program,@RequestParam(defaultValue="0") @Min(0) @Max(100) int semester,
        @RequestParam(defaultValue="0") @Min(0) @Max(9999) int batch,@RequestParam(defaultValue="OVERDUE") String scope,
        @RequestParam(defaultValue="ALL") String aging,@RequestParam(defaultValue="") String outcome,@RequestParam(defaultValue="0") @Min(0) int page){return service.list(search,program,semester,batch,scope,aging,outcome,page);}
    @GetMapping("/{admission}/followups") public Page<FeeFollowupService.Followup> history(@PathVariable @Size(max=30) String admission,@RequestParam(defaultValue="0") @Min(0) int page){return service.history(admission,page);}
    @PostMapping("/{admission}/followups") public FeeFollowupService.Followup record(@PathVariable @Size(max=30) String admission,@Valid @RequestBody FeeFollowupService.Input input,Principal actor){return service.record(admission,input,actor.getName());}
    @GetMapping("/{admission}/reminder-draft") public FeeFollowupService.ReminderDraft draft(@PathVariable @Size(max=30) String admission,jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return service.draft(admission);}
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public com.university.erp.dto.ApiError invalid(){return com.university.erp.dto.ApiError.of(400,"Invalid follow-up request, filter or page parameter");}
}
