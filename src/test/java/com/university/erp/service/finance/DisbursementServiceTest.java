package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class DisbursementServiceTest {
 @Test void approvedNetPayoutsUseARealCurrentOrPastMonth(){
  var today=LocalDate.of(2026,11,7);
  assertDoesNotThrow(()->DisbursementService.month(LocalDate.of(2026,11,1),today));
  assertDoesNotThrow(()->DisbursementService.month(LocalDate.of(2025,1,1),today));
  assertThrows(IllegalArgumentException.class,()->DisbursementService.month(LocalDate.of(2026,12,1),today));
  assertThrows(IllegalArgumentException.class,()->DisbursementService.month(LocalDate.of(2026,11,2),today));
 }
}
