package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class FeeFollowupServiceTest {
 private final LocalDate today=LocalDate.of(2026,11,7);
 private FeeFollowupService.Input input(String channel,String outcome,LocalDate promise,BigDecimal amount,LocalDate next){return new FeeFollowupService.Input(UUID.randomUUID(),channel,outcome,"Contact note",promise,amount,next);}
 @Test void validContactAndPromiseCanBeRecorded(){
  assertDoesNotThrow(()->FeeFollowupService.validate(input("PHONE","CONTACTED",null,null,today),today));
  assertDoesNotThrow(()->FeeFollowupService.validate(input("EMAIL","PROMISED",today.plusDays(3),new BigDecimal("100.25"),today.plusDays(1)),today));
 }
 @Test void promisesRequireFutureOrTodayDateAndPositiveAmount(){
  assertThrows(IllegalArgumentException.class,()->FeeFollowupService.validate(input("PHONE","PROMISED",null,BigDecimal.ONE,null),today));
  assertThrows(IllegalArgumentException.class,()->FeeFollowupService.validate(input("PHONE","PROMISED",today,BigDecimal.ZERO,null),today));
  assertThrows(IllegalArgumentException.class,()->FeeFollowupService.validate(input("PHONE","PROMISED",today.minusDays(1),BigDecimal.ONE,null),today));
 }
 @Test void nonPromiseStatusesCannotRetainPromiseFields(){assertThrows(IllegalArgumentException.class,()->FeeFollowupService.validate(input("PHONE","DISPUTED",today,BigDecimal.ONE,null),today));}
 @Test void invalidStatusesAndPastNextContactAreRejected(){
  assertThrows(IllegalArgumentException.class,()->FeeFollowupService.validate(input("SMS","CONTACTED",null,null,null),today));
  assertThrows(IllegalArgumentException.class,()->FeeFollowupService.validate(input("PHONE","PAID",null,null,null),today));
  assertThrows(IllegalArgumentException.class,()->FeeFollowupService.validate(input("PHONE","OPEN",null,null,today.minusDays(1)),today));
 }
}
