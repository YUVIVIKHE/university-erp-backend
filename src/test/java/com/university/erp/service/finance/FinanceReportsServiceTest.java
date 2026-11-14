package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class FinanceReportsServiceTest {
 @Test void boundedInclusivePeriods(){
  var from=LocalDate.of(2026,1,1);
  assertDoesNotThrow(()->FinanceReportsService.validate(from,from));
  assertDoesNotThrow(()->FinanceReportsService.validate(from,from.plusYears(5).minusDays(1)));
  assertThrows(IllegalArgumentException.class,()->FinanceReportsService.validate(from,from.minusDays(1)));
  assertThrows(IllegalArgumentException.class,()->FinanceReportsService.validate(from,from.plusYears(5)));
  assertThrows(IllegalArgumentException.class,()->FinanceReportsService.validate(null,from));
 }
}
