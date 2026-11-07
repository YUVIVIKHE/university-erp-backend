package com.university.erp.service.finance;
import com.university.erp.exception.ConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class OperatingFinanceServiceTest {
 private final LocalDate today=LocalDate.of(2026,11,7);
 private OperatingFinanceService.Input input(String direction,String category,LocalDate date){return new OperatingFinanceService.Input(UUID.randomUUID(),direction,category,"University event","DOC-1",date,BigDecimal.TEN,"Supporting evidence");}
 @Test void categoriesStayOutsideOtherFinanceModules(){
  assertDoesNotThrow(()->OperatingFinanceService.validate(input("INCOME","DONATION",today),today));
  assertDoesNotThrow(()->OperatingFinanceService.validate(input("EXPENSE","UTILITIES",today),today));
  for(String category:new String[]{"STUDENT_FEE","REFUND","PAYROLL","VENDOR_PAYMENT","DONATION"})assertThrows(IllegalArgumentException.class,()->OperatingFinanceService.validate(input("EXPENSE",category,today),today));
 }
 @Test void futureDocumentsCannotEnterTheRegister(){assertThrows(IllegalArgumentException.class,()->OperatingFinanceService.validate(input("INCOME","GRANT",today.plusDays(1)),today));}
 @Test void creatorCannotApproveOwnEntryButCanWithdraw(){
  assertThrows(ConflictException.class,()->OperatingFinanceService.validateReview("PENDING","maker","maker",new OperatingFinanceService.Review("APPROVED","Checked")));
  assertDoesNotThrow(()->OperatingFinanceService.validateReview("PENDING","maker","maker",new OperatingFinanceService.Review("CANCELLED","Withdraw")));
  assertThrows(ConflictException.class,()->OperatingFinanceService.validateReview("PENDING","maker","reviewer",new OperatingFinanceService.Review("CANCELLED","Withdraw")));
 }
 @Test void finalDecisionsCannotBeOverwritten(){for(String state:new String[]{"APPROVED","REJECTED","CANCELLED","SETTLED"})assertThrows(ConflictException.class,()->OperatingFinanceService.validateReview(state,"maker","reviewer",new OperatingFinanceService.Review("APPROVED","Changed")));}
 @Test void settlementRequiresApprovalAndValidDate(){
  assertDoesNotThrow(()->OperatingFinanceService.validateSettlement("APPROVED",today.minusDays(1),today,today));
  assertThrows(ConflictException.class,()->OperatingFinanceService.validateSettlement("PENDING",today,today,today));
  assertThrows(IllegalArgumentException.class,()->OperatingFinanceService.validateSettlement("APPROVED",today,today.plusDays(1),today));
  assertThrows(IllegalArgumentException.class,()->OperatingFinanceService.validateSettlement("APPROVED",today,today.minusDays(1),today));
 }
}
