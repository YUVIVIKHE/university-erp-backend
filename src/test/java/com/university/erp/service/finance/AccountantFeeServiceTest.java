package com.university.erp.service.finance;

import com.university.erp.entity.Admission;
import com.university.erp.entity.Fee;
import com.university.erp.repository.AdmissionRepository;
import com.university.erp.repository.finance.AccountantFeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountantFeeServiceTest {
    private final AccountantFeeRepository fees = mock(AccountantFeeRepository.class);
    private final AdmissionRepository admissions = mock(AdmissionRepository.class);
    private final AccountantFeeService service = new AccountantFeeService(fees, admissions);
    private AccountantFeeService.AssignRequest request() {
        return new AccountantFeeService.AssignRequest("ADM-1", " Tuition ", new BigDecimal("100.50"), LocalDate.of(2027,1,1), "Test");
    }
    @Test void assignmentUsesExistingApprovedAdmissionAndUniqueFeeIds() {
        Admission admission = new Admission(); admission.setAdmissionId("ADM-1"); admission.setStatus(Admission.Status.APPROVED);
        when(admissions.findByAdmissionId("ADM-1")).thenReturn(Optional.of(admission));
        when(fees.save(any())).thenAnswer(call -> call.getArgument(0));
        var one = service.assign(request()); var two = service.assign(request());
        assertEquals("ADM-1", one.admissionId()); assertEquals("Tuition", one.feeType());
        assertEquals(new BigDecimal("100.50"), one.balance()); assertEquals("PENDING", one.status());
        assertNotEquals(one.feeId(), two.feeId()); assertEquals(30, one.feeId().length());
        verify(admissions, never()).save(any());
    }
    @Test void rejectsUnapprovedOrMissingAdmissionsWithoutWriting() {
        Admission admission = new Admission(); admission.setStatus(Admission.Status.PENDING);
        when(admissions.findByAdmissionId("ADM-1")).thenReturn(Optional.of(admission));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.assign(request())).getStatusCode().value());
        when(admissions.findByAdmissionId("ADM-1")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.assign(request())).getStatusCode().value());
        verify(fees, never()).save(any());
    }
    @Test void invalidStatusAndMissingFeeAreExplicitErrors() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.list("", "INVALID", 0)).getStatusCode().value());
        when(fees.find("missing")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.detail("missing")).getStatusCode().value());
    }
}
