package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
class FinanceWorkspaceServiceTest {
 @Test void onlyPendingCreatorCanCorrectRequest(){
  assertDoesNotThrow(()->FinanceWorkspaceService.pendingOwner(Map.of("status","PENDING","created_by","maker"),"maker"));
  assertThrows(com.university.erp.exception.ConflictException.class,()->FinanceWorkspaceService.pendingOwner(Map.of("status","PENDING","created_by","maker"),"other"));
  for(String status:new String[]{"APPROVED","SETTLED","REJECTED","CANCELLED"})assertThrows(com.university.erp.exception.ConflictException.class,()->FinanceWorkspaceService.pendingOwner(Map.of("status",status,"created_by","maker"),"maker"));
 }
 @Test void historyKindsUseFixedTableAllowlist(){
  assertEquals("finance_budgets",FinanceWorkspaceService.table("BUDGET"));
  assertThrows(IllegalArgumentException.class,()->FinanceWorkspaceService.table("users"));
  assertThrows(IllegalArgumentException.class,()->FinanceWorkspaceService.table("ENTRY;DROP TABLE users"));
 }
}
