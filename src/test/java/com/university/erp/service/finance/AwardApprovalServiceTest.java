package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AwardApprovalServiceTest {
 private AwardApprovalService.Input input(String outcome,FeeCollectionService.Allocation... lines){return new AwardApprovalService.Input(UUID.randomUUID(),outcome,"Verified award",List.of(lines));}
 private FeeCollectionService.Allocation line(String id,String amount){return new FeeCollectionService.Allocation(id,new BigDecimal(amount));}
 @Test void approvalIsCappedByRequestedAmount(){
  assertEquals(new BigDecimal("75.25"),AwardApprovalService.validate(input("POSTED",line("F1","50"),line("F2","25.25")),new BigDecimal("100")));
  assertThrows(IllegalArgumentException.class,()->AwardApprovalService.validate(input("POSTED",line("F1","101")),new BigDecimal("100")));
 }
 @Test void declineNeverPostsAnAllocation(){
  assertEquals(BigDecimal.ZERO,AwardApprovalService.validate(input("DECLINED"),BigDecimal.TEN));
  assertThrows(IllegalArgumentException.class,()->AwardApprovalService.validate(input("DECLINED",line("F1","1")),BigDecimal.TEN));
 }
 @Test void emptyOrDuplicatePostingCannotInflateConcession(){
  assertThrows(IllegalArgumentException.class,()->AwardApprovalService.validate(input("POSTED"),BigDecimal.TEN));
  assertThrows(IllegalArgumentException.class,()->AwardApprovalService.validate(input("POSTED",line("F1","1"),line("F1","2")),BigDecimal.TEN));
 }
 @Test void concessionSettlesDebtWithoutBecomingCash(){
  var f=com.university.erp.entity.Fee.builder().amount(new BigDecimal("100")).concessionAmount(new BigDecimal("60")).amountPaid(new BigDecimal("40")).build();
  assertEquals(new BigDecimal("40"),f.netAmount());
  assertEquals(BigDecimal.ZERO,FeeCollectionService.validateBalance(f.netAmount(),new BigDecimal("20"),new BigDecimal("20"),"PARTIAL"));
  assertThrows(com.university.erp.exception.ConflictException.class,()->FeeCollectionService.validateBalance(f.netAmount(),f.getAmountPaid(),BigDecimal.ONE,"PAID"));
  assertEquals(new BigDecimal("100"),f.getAmount());assertEquals(new BigDecimal("40"),f.getAmountPaid());
 }
}
