package com.university.erp.service.finance;
import com.university.erp.exception.ConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class FeeRefundServiceTest {
 @Test void reservationsAndPaidRefundsReduceAvailableReceiptCapacity(){
  assertEquals(new BigDecimal("10"),FeeRefundService.refundable(new BigDecimal("100"),new BigDecimal("60"),new BigDecimal("30")));
  assertThrows(ConflictException.class,()->FeeRefundService.refundable(new BigDecimal("100"),new BigDecimal("60"),new BigDecimal("41")));
  assertThrows(IllegalArgumentException.class,()->FeeRefundService.refundable(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO));
 }
 @Test void requesterCannotApproveOrRejectOwnRefund(){for(String status:new String[]{"APPROVED","REJECTED"})assertThrows(ConflictException.class,()->FeeRefundService.validateReview("PENDING","maker","maker",new FeeRefundService.Review(status,"Reason")));}
 @Test void withdrawalBelongsToRequesterOnly(){
  assertDoesNotThrow(()->FeeRefundService.validateReview("PENDING","maker","maker",new FeeRefundService.Review("CANCELLED","Withdraw")));
  assertThrows(ConflictException.class,()->FeeRefundService.validateReview("PENDING","maker","reviewer",new FeeRefundService.Review("CANCELLED","Withdraw")));
 }
 @Test void terminalDecisionsCannotBeOverwritten(){
  assertDoesNotThrow(()->FeeRefundService.validateReview("PENDING","maker","reviewer",new FeeRefundService.Review("APPROVED","Checked")));
  for(String state:new String[]{"APPROVED","REJECTED","CANCELLED","PAID"})assertThrows(ConflictException.class,()->FeeRefundService.validateReview(state,"maker","reviewer",new FeeRefundService.Review("APPROVED","Changed")));
 }
}
