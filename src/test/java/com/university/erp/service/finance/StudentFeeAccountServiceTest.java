package com.university.erp.service.finance;

import com.university.erp.repository.finance.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class StudentFeeAccountServiceTest {
    private final StudentFeeAccountRepository accounts=mock(StudentFeeAccountRepository.class);
    private final AccountantFeeRepository fees=mock(AccountantFeeRepository.class);
    private final StudentFeeAccountService service=new StudentFeeAccountService(accounts,fees);
    @Test void directoryBoundsAggregationToRequestedPage() {
        when(accounts.accounts(eq("Name"),eq("B.Tech"),eq(2),eq(2026),eq(""),any(),eq(20),eq(40L))).thenReturn(List.of());
        when(accounts.countAccounts("Name","B.Tech",2,2026,"")).thenReturn(42L);
        var page=service.directory(" Name "," B.Tech ",2,2026,2);
        assertEquals(42,page.getTotalElements()); assertEquals(20,page.getSize());
        verifyNoInteractions(fees);
    }
    @Test void missingAccountDoesNotReadUnrelatedCharges() {
        when(accounts.accounts(eq(""),eq(""),eq(0),eq(0),eq("ADM-MISSING"),any(),eq(1),eq(0L))).thenReturn(List.of());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.account("ADM-MISSING",0)).getStatusCode().value());
        verifyNoInteractions(fees);
    }
    @Test void existingAccountWithNoFeesIsVisible() {
        var row=mock(StudentFeeAccountRepository.Account.class);
        when(row.getAdmissionId()).thenReturn("ADM-1"); when(row.getFeeCount()).thenReturn(0L);
        when(accounts.accounts(eq(""),eq(""),eq(0),eq(0),eq("ADM-1"),any(),eq(1),eq(0L))).thenReturn(List.of(row));
        when(fees.accountCharges("ADM-1",PageRequest.of(0,20))).thenReturn(Page.empty());
        var result=service.account("ADM-1",0);
        assertEquals("ADM-1",result.summary().admissionId()); assertEquals(0,result.summary().feeCount());
        assertTrue(result.charges().isEmpty());
    }
}
