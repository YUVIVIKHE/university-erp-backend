package com.university.erp.service.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class FinanceReportDetailsService {
 private final NamedParameterJdbcTemplate jdbc;
 @org.springframework.beans.factory.annotation.Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
 public record Detail(String section,String basis,LocalDate from,LocalDate until,List<String> columns,Map<String,Object> totals,Page<Map<String,Object>> rows){}
 public Detail detail(String section,String group,LocalDate from,LocalDate until,int page){
  FinanceReportsService.validate(from,until);
  if(page<0||page>100000)throw new IllegalArgumentException("Invalid report page");
  var p=new MapSqlParameterSource("from",java.sql.Date.valueOf(from)).addValue("until",java.sql.Date.valueOf(until)).addValue("offset",page*20L);
  String query,basis;List<String> columns;String totals;
  switch(section){
   case "collections" -> {
    String dimension=switch(group){case "program"->"p.program";case "year"->"'Year '||ceil(a.semester/2.0)::int";case "batch"->"a.batch_year::text";case "method"->"p.method";case "head"->"x.fee_type";default->throw new IllegalArgumentException("Invalid collection grouping");};
    String amount=group.equals("head")?"x.amount":"p.amount";
    query="SELECT "+dimension+" AS label,count(DISTINCT p.id) AS receipts,sum("+amount+") AS collected FROM finance_fee_payments p JOIN admissions a ON a.admission_id=p.admission_id "+(group.equals("head")?"JOIN finance_fee_payment_allocations x ON x.payment_id=p.id ":"")+"WHERE p.receipt_number IS NOT NULL AND p.paid_date BETWEEN :from AND :until GROUP BY 1";
    // Receipt counts are distinct across the whole period, not summed across fee heads.
    totals="SELECT count(*) AS receipts,coalesce(sum(amount),0) AS collected FROM finance_fee_payments WHERE receipt_number IS NOT NULL AND paid_date BETWEEN :from AND :until";
    columns=List.of("label","receipts","collected");basis="Gross verified receipts by payment date. Refunds are reported separately. Student year/batch use current admission data; fee heads use saved allocations.";
   }
   case "balances", "ageing", "defaulters" -> {
    String base="WITH balances AS(SELECT f.*,a.first_name||' '||a.last_name AS student,a.program,greatest(f.amount-f.concession_amount-f.amount_paid,0) AS balance FROM fees f JOIN admissions a ON a.admission_id=f.admission_id WHERE f.status<>'WAIVED') ";
    String aggregate="count(DISTINCT admission_id) AS students,coalesce(sum(amount-concession_amount),0) AS assessed,coalesce(sum(amount_paid),0) AS paid,coalesce(sum(balance),0) AS outstanding,coalesce(sum(balance) FILTER(WHERE due_date<:until),0) AS overdue,coalesce(round(100*sum(amount_paid)/nullif(sum(amount-concession_amount),0),2),0) AS collection_percent";
    if(section.equals("ageing")){
     query=base+"SELECT CASE WHEN due_date>=:until THEN 'Not overdue' WHEN :until-due_date<=30 THEN '01-30 days' WHEN :until-due_date<=60 THEN '31-60 days' WHEN :until-due_date<=90 THEN '61-90 days' ELSE '91+ days' END AS label,count(DISTINCT admission_id) AS students,sum(balance) AS outstanding FROM balances WHERE balance>0 GROUP BY 1";
     columns=List.of("label","students","outstanding");
    }else if(section.equals("defaulters")){
     query=base+"SELECT admission_id AS label,max(student) AS student,max(program) AS program,sum(balance) AS outstanding,sum(balance) FILTER(WHERE due_date<:until) AS overdue FROM balances GROUP BY admission_id HAVING sum(balance) FILTER(WHERE due_date<:until)>0";
     columns=List.of("label","student","program","outstanding","overdue");
    }else{
     query=base+"SELECT program AS label,"+aggregate+" FROM balances GROUP BY program";
     columns=List.of("label","students","assessed","paid","outstanding","overdue","collection_percent");
    }
    totals=base+"SELECT "+aggregate+" FROM balances";
    basis="Current fee balances, not a historical balance reconstruction. Ageing uses today's institution date; report date filters do not change these balances. Waived fees excluded.";
    var today=LocalDate.now(java.time.ZoneId.of(timeZone));
    basis+=" As of "+today+".";
    p.addValue("until",java.sql.Date.valueOf(today));
   }
   case "awards" -> {
    query="SELECT s.category||' / '||s.funding||' / '||s.status||' / '||coalesce(d.outcome,'UNDECIDED') AS label,count(*) AS applications,sum(s.requested_amount) AS requested,coalesce(sum(d.amount) FILTER(WHERE d.outcome='POSTED'),0) AS posted FROM finance_scholarship_applications s LEFT JOIN finance_award_decisions d ON d.application_id=s.id WHERE (s.created_at AT TIME ZONE 'Asia/Kolkata')::date BETWEEN :from AND :until GROUP BY 1";
    totals="SELECT coalesce(sum(applications),0) AS applications,coalesce(sum(requested),0) AS requested,coalesce(sum(posted),0) AS posted FROM ("+query+") q";
    columns=List.of("label","applications","requested","posted");basis="Applications created in the period, with current review/award status. Posted concessions are fee reductions, not incoming cash or external scholarship disbursements.";
   }
   case "refunds" -> {
    query="SELECT status AS label,count(*) AS requests,sum(amount) AS amount FROM finance_refunds WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date BETWEEN :from AND :until GROUP BY status";
    totals="SELECT count(*) AS requests,coalesce(sum(amount),0) AS requested,coalesce(sum(amount) FILTER(WHERE status='PAID'),0) AS paid FROM finance_refunds WHERE (created_at AT TIME ZONE 'Asia/Kolkata')::date BETWEEN :from AND :until";
    columns=List.of("label","requests","amount");basis="Requests created in the period with current status. Cash refunds by actual payout date appear in the cash report.";
   }
   case "budgets" -> {
    query="SELECT b.name||' / '||l.category AS label,b.period_start,b.period_end,l.amount AS allocated,coalesce(a.actual,0) AS actual,coalesce(c.committed,0) AS committed,l.amount-coalesce(a.actual,0) AS variance,l.amount-coalesce(a.actual,0)-coalesce(c.committed,0) AS available,round(100*coalesce(a.actual,0)/l.amount,2) AS utilization_percent FROM finance_budgets b JOIN finance_budget_lines l ON l.budget_id=b.id LEFT JOIN LATERAL(SELECT sum(o.amount) AS actual FROM finance_operating_entries o WHERE o.direction='EXPENSE' AND o.status='SETTLED' AND o.category=l.category AND o.settlement_date BETWEEN b.period_start AND b.period_end) a ON true LEFT JOIN LATERAL(SELECT sum(o.amount) AS committed FROM finance_operating_entries o WHERE o.direction='EXPENSE' AND o.status='APPROVED' AND o.category=l.category AND o.document_date BETWEEN b.period_start AND b.period_end) c ON true WHERE b.status='APPROVED' AND b.period_start<=:until AND b.period_end>=:from";
    totals="SELECT coalesce(sum(allocated),0) AS allocated,coalesce(sum(actual),0) AS actual,coalesce(sum(committed),0) AS committed,coalesce(sum(variance),0) AS variance FROM ("+query+") q";
    columns=List.of("label","period_start","period_end","allocated","actual","committed","variance","available","utilization_percent");basis="Approved budgets overlapping selected dates. Each line shows its complete budget-period actuals and current commitments; allocations are not prorated. Payroll/vendor payments are outside existing category budgets.";
   }
   case "payroll", "vendors" -> {
    String kind=section.equals("payroll")?"staff_user_id IS NOT NULL":"vendor_id IS NOT NULL";
    query="SELECT "+(section.equals("payroll")?"to_char(payroll_month,'YYYY-MM')":"counterparty")+"||' / '||status AS label,count(*) AS requests,sum(amount) AS amount FROM finance_operating_entries WHERE "+kind+" AND document_date BETWEEN :from AND :until GROUP BY 1";
    totals="SELECT count(*) AS requests,coalesce(sum(amount) FILTER(WHERE status='SETTLED'),0) AS paid,coalesce(sum(amount) FILTER(WHERE status='APPROVED'),0) AS approved_unpaid,coalesce(sum(amount) FILTER(WHERE status='PENDING'),0) AS pending FROM finance_operating_entries WHERE "+kind+" AND document_date BETWEEN :from AND :until";
    columns=List.of("label","requests","amount");basis="Requests by document date with current status. Pending is unapproved; approved unpaid is an obligation. Settled cash by payment date is in the cash report; payroll amounts are net payouts.";
   }
   default -> throw new IllegalArgumentException("Unknown finance report");
  }
  long count=jdbc.queryForObject("SELECT count(*) FROM ("+query+") q",p,Long.class);
  var summary=jdbc.queryForMap(totals,p);
  var rows=jdbc.queryForList("SELECT * FROM ("+query+") q ORDER BY label"+(section.equals("budgets")?",period_start,period_end":"")+" LIMIT 20 OFFSET :offset",p);
  return new Detail(section,basis,from,until,columns,summary,new PageImpl<>(rows,PageRequest.of(page,20),count));
 }
}
