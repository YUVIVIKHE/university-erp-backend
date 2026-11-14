package com.university.erp.service.finance;
import com.university.erp.exception.ConflictException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class FeeRefundServiceTest {
 @Test void staleWorkflowActionsCannotChangeRefund(){
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class);
  var service=new FeeRefundService(jdbc,new com.fasterxml.jackson.databind.ObjectMapper());var id=java.util.UUID.randomUUID();
  org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FOR UPDATE"),org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class))).thenReturn(java.util.List.of(java.util.Map.of("version",2L,"status","PENDING","created_by","maker")));
  assertThrows(ConflictException.class,()->service.action(id,new FeeRefundService.Action(1,"VERIFY","Checked"),"reviewer"));
  org.mockito.Mockito.verify(jdbc,org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class));
 }
 @Test void requesterCannotVerifyOwnRefundOrEditFinalRequest(){
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class);
  var service=new FeeRefundService(jdbc,new com.fasterxml.jackson.databind.ObjectMapper());var id=java.util.UUID.randomUUID();
  org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FOR UPDATE"),org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class))).thenReturn(java.util.List.of(java.util.Map.of("version",0L,"status","PENDING","created_by","maker")));
  assertThrows(ConflictException.class,()->service.action(id,new FeeRefundService.Action(0,"VERIFY","Checked"),"maker"));
  org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FOR UPDATE"),org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class))).thenReturn(java.util.List.of(java.util.Map.of("version",0L,"status","PAID","created_by","maker")));
  assertThrows(ConflictException.class,()->service.edit(id,new FeeRefundService.Edit(0,null,"Correction"),"maker"));
 }
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
