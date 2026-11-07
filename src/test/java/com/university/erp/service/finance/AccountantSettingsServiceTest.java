package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class AccountantSettingsServiceTest {
 @Test void financialYearUsesAprilBoundary(){
  assertEquals(LocalDate.of(2025,4,1),AccountantSettingsService.start(LocalDate.of(2026,3,31),"FINANCIAL_YEAR"));
  assertEquals(LocalDate.of(2026,4,1),AccountantSettingsService.start(LocalDate.of(2026,4,1),"FINANCIAL_YEAR"));
 }
 @Test void monthAndCalendarYearDefaults(){
  var date=LocalDate.of(2024,2,29);
  assertEquals(LocalDate.of(2024,2,1),AccountantSettingsService.start(date,"MONTH"));
  assertEquals(LocalDate.of(2024,1,1),AccountantSettingsService.start(date,"YEAR"));
  assertThrows(IllegalArgumentException.class,()->AccountantSettingsService.start(date,"OTHER"));
 }
}
