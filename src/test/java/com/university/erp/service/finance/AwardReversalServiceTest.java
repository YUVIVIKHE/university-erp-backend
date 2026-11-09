package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AwardReversalServiceTest {
 @Test void requesterCannotApproveOrRejectOwnReversal(){
  for(String status:new String[]{"APPROVED","REJECTED"})assertThrows(com.university.erp.exception.ConflictException.class,()->AwardReversalService.validateReviewer(status,"maker","maker"));
 }
 @Test void separateReviewerMustChooseSupportedFinalDecision(){
  assertDoesNotThrow(()->AwardReversalService.validateReviewer("APPROVED","maker","reviewer"));
  assertDoesNotThrow(()->AwardReversalService.validateReviewer("REJECTED","maker","reviewer"));
  assertThrows(IllegalArgumentException.class,()->AwardReversalService.validateReviewer("PENDING","maker","reviewer"));
 }
}
