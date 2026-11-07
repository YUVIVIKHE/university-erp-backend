package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PayrollSetupServiceTest {
 private PayrollSetupService.Salary salary(String basic,String allowances,String deductions,LocalDate effective){return new PayrollSetupService.Salary(UUID.randomUUID(),1L,effective,new BigDecimal(basic),new BigDecimal(allowances),new BigDecimal(deductions),"HR reference");}
 @Test void fixedAmountsUseExactDecimalArithmetic(){assertEquals(new BigDecimal("1150.30"),PayrollSetupService.net(salary("1000.25","200.10","50.05",LocalDate.of(2026,1,1))));}
 @Test void zeroOrNegativeNetIsRejected(){for(String deduction:new String[]{"100","101"})assertThrows(IllegalArgumentException.class,()->PayrollSetupService.net(salary("100","0",deduction,LocalDate.of(2026,1,1))));}
 @Test void monthlyVersionsAndGrossLimitsAreEnforced(){
  assertThrows(IllegalArgumentException.class,()->PayrollSetupService.net(salary("100","0","0",LocalDate.of(2026,1,2))));
  assertThrows(IllegalArgumentException.class,()->PayrollSetupService.net(salary("9999999999.99","1","0",LocalDate.of(2026,1,1))));
 }
}
