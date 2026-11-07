package com.university.erp.service.finance;

import com.university.erp.repository.finance.StudentFeeAccountRepository;
import com.university.erp.repository.finance.AccountantFeeRepository;
import com.university.erp.dto.finance.FinanceDashboard.FeeItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class StudentFeeAccountService {
    private final StudentFeeAccountRepository accounts;
    private final AccountantFeeRepository fees;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone = "Asia/Kolkata";
    public record Summary(String admissionId, String studentName, String program, int semester, int batchYear,
        String admissionStatus, long feeCount, BigDecimal billed, BigDecimal paid, BigDecimal outstanding,
        BigDecimal overdue, BigDecimal waived, BigDecimal concessions) {
        static Summary from(StudentFeeAccountRepository.Account a) {
            return new Summary(a.getAdmissionId(),a.getStudentName(),a.getProgram(),a.getSemester(),a.getBatchYear(),
                a.getAdmissionStatus(),a.getFeeCount(),a.getBilled(),a.getPaid(),a.getOutstanding(),a.getOverdue(),a.getWaived(),a.getConcessions());
        }
    }
    public record AccountDetail(Summary summary, Page<FeeItem> charges, LocalDate asOf) {}
    private LocalDate today() { return LocalDate.now(ZoneId.of(timeZone)); }
    public Page<Summary> directory(String search, String program, int semester, int batch, int page) {
        var pageable = PageRequest.of(page,20);
        var rows = accounts.accounts(search.trim(),program.trim(),semester,batch,"",today(),20,pageable.getOffset());
        return new PageImpl<>(rows.stream().map(Summary::from).toList(),pageable,
            accounts.countAccounts(search.trim(),program.trim(),semester,batch,""));
    }
    public AccountDetail account(String id, int page) {
        LocalDate date = today();
        var row = accounts.accounts("","",0,0,id,date,1,0).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Fee account not found"));
        var charges = fees.accountCharges(id,PageRequest.of(page,20)).map(f -> FinanceDashboardService.item(f,date));
        return new AccountDetail(Summary.from(row),charges,date);
    }
}
