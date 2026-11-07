package com.university.erp.service.finance;
import com.university.erp.exception.ConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FeeCollectionServiceTest {
 @Test void partialAndFullPaymentsHaveExactBalances(){
  assertEquals(new BigDecimal("79.75"),FeeCollectionService.validateBalance(new BigDecimal("100.00"),new BigDecimal("10.00"),new BigDecimal("10.25"),"PARTIAL"));
  assertEquals(BigDecimal.ZERO,FeeCollectionService.validateBalance(new BigDecimal("100"),new BigDecimal("10"),new BigDecimal("90"),"PARTIAL"));
 }
 @Test void overpaymentsWaiversAndNonPositivePaymentsAreRejected(){
  assertThrows(ConflictException.class,()->FeeCollectionService.validateBalance(BigDecimal.TEN,BigDecimal.ONE,BigDecimal.TEN,"PENDING"));
  assertThrows(IllegalArgumentException.class,()->FeeCollectionService.validateBalance(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ONE,"WAIVED"));
  assertThrows(IllegalArgumentException.class,()->FeeCollectionService.validateBalance(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING"));
 }
 @Test void allocationsHaveConsistentLockOrderAndRejectDuplicates(){
  var first=new FeeCollectionService.Allocation("FEE-A",BigDecimal.ONE);var second=new FeeCollectionService.Allocation("FEE-B",BigDecimal.TEN);
  assertEquals(List.of(first,second),FeeCollectionService.ordered(List.of(second,first)));
  assertThrows(IllegalArgumentException.class,()->FeeCollectionService.ordered(List.of(first,first)));
 }
 @Test void onlySupportedPaymentMethodsAreAccepted(){assertEquals("BANK_TRANSFER",FeeCollectionService.method(" bank_transfer "));assertThrows(IllegalArgumentException.class,()->FeeCollectionService.method("UNVERIFIED"));}
}
