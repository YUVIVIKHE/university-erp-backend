package com.university.erp.service;

import com.university.erp.dto.FeeRequest;
import com.university.erp.dto.FeeResponse;
import com.university.erp.entity.Admission;
import com.university.erp.entity.Fee;
import com.university.erp.repository.AdmissionRepository;
import com.university.erp.repository.FeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeRepository feeRepo;
    private final AdmissionRepository admissionRepo;

    @Transactional
    public FeeResponse create(FeeRequest req) {
        var admission = admissionRepo.findByAdmissionId(req.admissionId)
            .orElseThrow(() -> new NoSuchElementException("Admission not found: " + req.admissionId));

        Fee fee = Fee.builder()
            .feeId(generateFeeId())
            .admission(admission)
            .feeType(req.feeType)
            .amount(req.amount)
            .amountPaid(BigDecimal.ZERO)
            .dueDate(req.dueDate)
            .status(Fee.Status.PENDING)
            .remarks(req.remarks)
            .build();

        return FeeResponse.from(feeRepo.save(fee));
    }

    public Page<FeeResponse> list(String search, String status, int page) {
        Fee.Status st = (status != null && !status.isBlank()) ? Fee.Status.valueOf(status) : null;
        return feeRepo.search(search == null ? "" : search, st, PageRequest.of(page, 20))
                      .map(FeeResponse::from);
    }

    public FeeResponse getByFeeId(String feeId) {
        return feeRepo.findByFeeId(feeId)
            .map(FeeResponse::from)
            .orElseThrow(() -> new NoSuchElementException("Fee not found: " + feeId));
    }

    @Transactional
    public FeeResponse pay(String feeId, FeeRequest.PayRequest req) {
        Fee fee = feeRepo.findByFeeIdForUpdate(feeId)
            .orElseThrow(() -> new NoSuchElementException("Fee not found: " + feeId));

        if (fee.getStatus() == Fee.Status.WAIVED) throw new IllegalArgumentException("Cannot collect payment against a waived fee");
        BigDecimal newPaid = fee.getAmountPaid().add(req.amountPaid);
        if (newPaid.compareTo(fee.netAmount()) > 0)
            throw new IllegalArgumentException("Payment exceeds fee amount");

        fee.setAmountPaid(newPaid);
        fee.setPaidDate(LocalDate.now());
        if (req.remarks != null) fee.setRemarks(req.remarks);

        if (newPaid.compareTo(fee.netAmount()) == 0) {
            fee.setStatus(Fee.Status.PAID);
        } else {
            fee.setStatus(Fee.Status.PARTIAL);
        }

        return FeeResponse.from(feeRepo.save(fee));
    }

    @Transactional
    public FeeResponse waive(String feeId, String remarks) {
        Fee fee = feeRepo.findByFeeIdForUpdate(feeId)
            .orElseThrow(() -> new NoSuchElementException("Fee not found: " + feeId));
        fee.setStatus(Fee.Status.WAIVED);
        if (remarks != null) fee.setRemarks(remarks);
        return FeeResponse.from(feeRepo.save(fee));
    }

    public record AdmissionSummary(
        String admissionId, String studentName, String program,
        String status, String createdAt,
        long feeCount, java.math.BigDecimal totalBilled, java.math.BigDecimal totalPaid
    ) {}

    public List<AdmissionSummary> admissionsForFees(int limit) {
        // recent admissions without any fee record first, then all recent
        List<Admission> noFee = admissionRepo.findAdmissionsWithoutFees(PageRequest.of(0, limit));
        return noFee.stream().map(a -> new AdmissionSummary(
            a.getAdmissionId(),
            a.getFirstName() + " " + a.getLastName(),
            a.getProgram(),
            a.getStatus().name(),
            a.getCreatedAt().toLocalDate().toString(),
            feeRepo.countByAdmission_AdmissionId(a.getAdmissionId()),
            feeRepo.totalAmountByAdmission(a.getAdmissionId()),
            feeRepo.totalPaidByAdmission(a.getAdmissionId())
        )).toList();
    }

    public FeeResponse.Stats stats() {
        return new FeeResponse.Stats(
            feeRepo.count(),
            feeRepo.countByStatus(Fee.Status.PAID),
            feeRepo.countByStatus(Fee.Status.PENDING) + feeRepo.countByStatus(Fee.Status.PARTIAL),
            feeRepo.countByStatus(Fee.Status.OVERDUE),
            feeRepo.totalAmount(),
            feeRepo.totalCollected()
        );
    }

    private String generateFeeId() {
        int year = Year.now().getValue();
        long count = feeRepo.count() + 1;
        String id;
        do {
            id = String.format("FEE-%d-%04d", year, count++);
        } while (feeRepo.existsByFeeId(id));
        return id;
    }
}
