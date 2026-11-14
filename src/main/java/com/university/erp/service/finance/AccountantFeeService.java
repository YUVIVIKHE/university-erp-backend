package com.university.erp.service.finance;

import com.university.erp.dto.finance.FinanceDashboard.FeeItem;
import com.university.erp.entity.Admission;
import com.university.erp.entity.Fee;
import com.university.erp.repository.AdmissionRepository;
import com.university.erp.repository.finance.AccountantFeeRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountantFeeService {
    private final AccountantFeeRepository fees;
    private final AdmissionRepository admissions;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone = "Asia/Kolkata";
    public record AssignRequest(@NotBlank String admissionId, @NotBlank @Size(max=60) String feeType,
        @NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal amount,
        @NotNull LocalDate dueDate, @Size(max=255) String remarks) {}
    public record AdmissionOption(String admissionId, String studentName, String program) {}
    public record Detail(FeeItem fee, String remarks, LocalDate paidDate) {}
    private LocalDate today() { return LocalDate.now(ZoneId.of(timeZone)); }
    public Page<FeeItem> list(String search, String status, int page) {
        if (!status.isEmpty()) {
            try { Fee.Status.valueOf(status); }
            catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid fee status"); }
        }
        LocalDate date = today();
        return fees.search(search.trim(), status, date, Fee.Status.WAIVED, PageRequest.of(page, 20))
            .map(f -> FinanceDashboardService.item(f, date));
    }
    public Detail detail(String id) {
        Fee fee = fees.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee not found"));
        return new Detail(FinanceDashboardService.item(fee, today()), fee.getRemarks(), fee.getPaidDate());
    }
    public Page<AdmissionOption> admissions(String search, int page) {
        return admissions.search(search.trim(), Admission.Status.APPROVED,
            PageRequest.of(page, 20, org.springframework.data.domain.Sort.by("id").descending()))
            .map(a -> new AdmissionOption(a.getAdmissionId(), a.getFirstName()+" "+a.getLastName(), a.getProgram()));
    }
    @Transactional
    public FeeItem assign(AssignRequest request) {
        Admission admission = admissions.findByAdmissionId(request.admissionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admission not found"));
        if (admission.getStatus() != Admission.Status.APPROVED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only approved admissions can be assigned fees");
        if (request.feeType().trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fee type is required");
        Fee fee = Fee.builder().feeId("FEE-"+UUID.randomUUID().toString().replace("-", "").substring(0,26))
            .admission(admission).feeType(request.feeType().trim()).amount(request.amount())
            .amountPaid(BigDecimal.ZERO).dueDate(request.dueDate()).status(Fee.Status.PENDING)
            .remarks(request.remarks()).build();
        return FinanceDashboardService.item(fees.save(fee), today());
    }
}
