package com.university.erp.service.finance;

import com.university.erp.dto.FeeRequest;
import com.university.erp.entity.Admission;
import com.university.erp.entity.Fee;
import com.university.erp.repository.AdmissionRepository;
import com.university.erp.repository.FeeRepository;
import com.university.erp.service.FeeService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyFeePaymentTest {
    private final FeeRepository fees=mock(FeeRepository.class);
    private final FeeService service=new FeeService(fees,mock(AdmissionRepository.class));
    private Fee fee(Fee.Status status){return Fee.builder().feeId("FEE-OLD").feeType("Tuition")
        .admission(Admission.builder().admissionId("ADM-OLD").firstName("Existing").lastName("Student").program("B.Tech").build())
        .amount(new BigDecimal("100.00")).amountPaid(new BigDecimal("25.00")).dueDate(LocalDate.now().plusDays(1)).status(status).build();}
    @Test void existingPartialPaymentStillUpdatesSharedBalance(){
        var fee=fee(Fee.Status.PARTIAL);when(fees.findByFeeIdForUpdate("FEE-OLD")).thenReturn(Optional.of(fee));
        when(fees.save(fee)).thenReturn(fee);var input=new FeeRequest.PayRequest();input.amountPaid=new BigDecimal("20.25");
        var result=service.pay("FEE-OLD",input);assertEquals(new BigDecimal("45.25"),result.amountPaid());
        assertEquals(new BigDecimal("54.75"),result.balance());assertEquals("PARTIAL",result.status());
    }
    @Test void waivedFeeCannotBePaidThroughLegacyRoute(){
        var fee=fee(Fee.Status.WAIVED);when(fees.findByFeeIdForUpdate("FEE-OLD")).thenReturn(Optional.of(fee));
        var input=new FeeRequest.PayRequest();input.amountPaid=BigDecimal.ONE;
        assertThrows(IllegalArgumentException.class,()->service.pay("FEE-OLD",input));
        assertEquals(new BigDecimal("25.00"),fee.getAmountPaid());verify(fees,never()).save(any());
    }
}
