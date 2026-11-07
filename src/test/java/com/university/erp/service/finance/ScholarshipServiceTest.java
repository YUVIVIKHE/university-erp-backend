package com.university.erp.service.finance;
import com.university.erp.exception.ConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ScholarshipServiceTest {
 private ScholarshipService.Input input(String category,String funding){return new ScholarshipService.Input(UUID.randomUUID(),"ADM-1","Merit",category,funding,2026,new BigDecimal("100"),"DOC-1","Eligibility checked");}
 @Test void externalScholarshipsAreDistinctFromInstitutionConcessions(){
  assertDoesNotThrow(()->ScholarshipService.validate(input("SCHOLARSHIP","EXTERNAL")));
  assertDoesNotThrow(()->ScholarshipService.validate(input("CONCESSION","INSTITUTION")));
  assertThrows(IllegalArgumentException.class,()->ScholarshipService.validate(input("CONCESSION","EXTERNAL")));
 }
 @Test void creatorCannotVerifyOrRejectOwnApplication(){for(String status:new String[]{"VERIFIED","REJECTED"})assertThrows(ConflictException.class,()->ScholarshipService.validateReview(new ScholarshipService.Review(status,"Checked"),"maker","maker"));}
 @Test void onlyCreatorCanWithdraw(){
  assertDoesNotThrow(()->ScholarshipService.validateReview(new ScholarshipService.Review("CANCELLED","Withdraw"),"maker","maker"));
  assertThrows(ConflictException.class,()->ScholarshipService.validateReview(new ScholarshipService.Review("CANCELLED","Withdraw"),"maker","reviewer"));
 }
 @Test void verificationCannotBecomeFinancialApproval(){
  assertDoesNotThrow(()->ScholarshipService.validateReview(new ScholarshipService.Review("VERIFIED","Documents checked"),"maker","reviewer"));
  assertThrows(IllegalArgumentException.class,()->ScholarshipService.validateReview(new ScholarshipService.Review("APPROVED","Pay"),"maker","reviewer"));
 }
}
