package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BudgetServiceTest {
 private BudgetService.Input input(LocalDate start,LocalDate end,BudgetService.Line... lines){return new BudgetService.Input(UUID.randomUUID(),"Operating plan",start,end,"Approved purpose",List.of(lines));}
 private BudgetService.Line line(String category,String amount){return new BudgetService.Line(category,new BigDecimal(amount));}
 @Test void allocationsUseExistingExpenseCategories(){
  var date=LocalDate.of(2027,1,1);
  assertEquals(new BigDecimal("125.25"),BudgetService.validate(input(date,date.plusMonths(1),line("UTILITIES","100"),line("MAINTENANCE","25.25"))));
  assertThrows(IllegalArgumentException.class,()->BudgetService.validate(input(date,date,line("DONATION","100"))));
 }
 @Test void duplicateCategoryCannotDoubleItsAllocation(){var date=LocalDate.of(2027,1,1);assertThrows(IllegalArgumentException.class,()->BudgetService.validate(input(date,date,line("UTILITIES","100"),line("UTILITIES","50"))));}
 @Test void reversedPeriodIsRejected(){var date=LocalDate.of(2027,1,1);assertThrows(IllegalArgumentException.class,()->BudgetService.validate(input(date,date.minusDays(1),line("UTILITIES","100"))));}
}
