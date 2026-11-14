package com.university.erp.service.finance;

import com.university.erp.entity.User;
import com.university.erp.exception.ConflictException;
import com.university.erp.service.admin.AdminAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class FinanceWorkspaceService {
 private final NamedParameterJdbcTemplate jdbc;
 private final AdminAuditService audit;
 public record Action(@Min(0) long version,@NotBlank String action,@NotBlank @Size(max=2000) String note){}
 public record EntryEdit(@Min(0) long version,@NotBlank @Size(max=150) String counterparty,@NotBlank @Size(max=150) String documentReference,@NotNull LocalDate documentDate,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank @Size(max=2000) String description,@Size(max=250) String evidenceReference,LocalDate targetDate,@NotBlank @Size(max=2000) String note){}
 public record BudgetEdit(@Min(0) long version,@NotNull @Valid BudgetService.Input application,@NotBlank @Size(max=2000) String note){}
 public record VendorEdit(@Min(0) long version,@NotBlank @Size(max=150) String name,@Email @Size(max=255) String email,@Size(max=30) String phone,@Size(max=500) String address,@NotBlank @Size(max=2000) String note){}
 public record Profile(@Min(0) long version,@NotBlank @Size(max=150) String fullName,@NotNull @Size(max=30) @Pattern(regexp="[0-9+() .-]*") String phone,@NotNull @Size(max=100) String designation,@NotNull @Size(max=120) String department,@NotNull @Size(max=150) String office){}
 static String table(String kind){return switch(kind){case "ENTRY"->"finance_operating_entries";case "BUDGET"->"finance_budgets";case "VENDOR"->"finance_vendors";default->throw new IllegalArgumentException("Invalid finance record type");};}
 private Map<String,Object> lock(String kind,UUID id,long version){var rows=jdbc.queryForList("SELECT * FROM "+table(kind)+" WHERE id=:id FOR UPDATE",Map.of("id",id));if(rows.isEmpty())throw new NoSuchElementException("Finance record not found");var r=rows.get(0);if(((Number)r.get("version")).longValue()!=version)throw new ConflictException("Record changed. Reload before saving.");return r;}

 private void snapshot(String kind,UUID id,String action,String actor,String note){jdbc.update("INSERT INTO finance_record_events(kind,record_id,action,actor,note,previous_values) SELECT :kind,id,:action,:actor,:note,to_jsonb(r) FROM "+table(kind)+" r WHERE id=:id",Map.of("kind",kind,"id",id,"action",action,"actor",actor,"note",note.trim()));}
 @Transactional(readOnly=true) public List<Map<String,Object>> history(String kind,UUID id){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+table(kind)+" WHERE id=:id)",Map.of("id",id),Boolean.class)))throw new NoSuchElementException("Finance record not found");return jdbc.queryForList("SELECT action,actor,note,created_at FROM finance_record_events WHERE kind=:kind AND record_id=:id ORDER BY id DESC LIMIT 100",Map.of("kind",kind,"id",id));}
 static void pendingOwner(Map<String,Object> r,String actor){if(!"PENDING".equals(r.get("status"))||!actor.equals(r.get("created_by")))throw new ConflictException("Only the creator can edit a pending request");}
 @Transactional public void editEntry(UUID id,EntryEdit input,String actor){
  var r=lock("ENTRY",id,input.version());pendingOwner(r,actor);
  if(input.documentDate().isAfter(LocalDate.now(ZoneId.of("Asia/Kolkata"))))throw new IllegalArgumentException("Document date cannot be in the future");
  boolean payroll=r.get("staff_user_id")!=null,vendor=r.get("vendor_id")!=null;
  if((payroll||vendor)&&!input.counterparty().trim().equals(r.get("counterparty")))throw new IllegalArgumentException("Payee identity cannot be changed on an existing request");
  if(payroll&&(input.amount().compareTo((BigDecimal)r.get("amount"))!=0||!input.documentReference().trim().equals(r.get("document_reference"))||!input.documentDate().equals(((java.sql.Date)r.get("document_date")).toLocalDate())))throw new ConflictException("Payroll amount, period and authorisation snapshot are locked. Withdraw and regenerate a corrected payout.");
  snapshot("ENTRY",id,"EDITED",actor,input.note());
  jdbc.update("UPDATE finance_operating_entries SET counterparty=:party,document_reference=:reference,document_date=:date,amount=:amount,description=:description,evidence_reference=:evidence,target_date=:target,version=version+1 WHERE id=:id",new MapSqlParameterSource("id",id).addValue("party",input.counterparty().trim()).addValue("reference",input.documentReference().trim()).addValue("date",input.documentDate()).addValue("amount",input.amount()).addValue("description",input.description().trim()).addValue("evidence",input.evidenceReference()).addValue("target",input.targetDate()));
 }
 @Transactional public void entryAction(UUID id,Action input,String actor){
  var r=lock("ENTRY",id,input.version());var p=new MapSqlParameterSource("id",id);String sql;
  switch(input.action()){
   case "NOTE" -> sql="UPDATE finance_operating_entries SET version=version+1 WHERE id=:id";
   case "RETURN" -> {if(!"APPROVED".equals(r.get("status"))||actor.equals(r.get("created_by"))||"PROCESSING".equals(r.get("progress")))throw new ConflictException("Another reviewer may return an unpaid approval when settlement is not processing");if(r.get("staff_user_id")!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=:id AND email=:actor)",Map.of("id",r.get("staff_user_id"),"actor",actor),Boolean.class)))throw new ConflictException("Payroll beneficiary cannot return their own payout approval");sql="UPDATE finance_operating_entries SET status='PENDING',progress='READY',reviewed_by=NULL,reviewed_at=NULL,review_note=NULL,version=version+1 WHERE id=:id";}
   case "READY","PROCESSING","FAILED" -> {if(!"APPROVED".equals(r.get("status")))throw new ConflictException("Only approved unpaid requests have settlement progress");p.addValue("progress",input.action());sql="UPDATE finance_operating_entries SET progress=:progress,version=version+1 WHERE id=:id";}
   default -> throw new IllegalArgumentException("Invalid workflow action");
  }
  snapshot("ENTRY",id,input.action(),actor,input.note());jdbc.update(sql,p);
 }
 @Transactional public void editBudget(UUID id,BudgetEdit input,String actor){
  var r=lock("BUDGET",id,input.version());pendingOwner(r,actor);var a=input.application();var total=BudgetService.validate(a);
  snapshot("BUDGET",id,"EDITED",actor,input.note());
  // Retain the actual prior line values in the correction snapshot.
  jdbc.update("UPDATE finance_record_events SET previous_values=previous_values||jsonb_build_object('lines',(SELECT jsonb_agg(to_jsonb(l)) FROM finance_budget_lines l WHERE l.budget_id=:id)) WHERE id=(SELECT max(id) FROM finance_record_events WHERE kind='BUDGET' AND record_id=:id)",Map.of("id",id));
  jdbc.update("UPDATE finance_budgets SET name=:name,period_start=:start,period_end=:end,notes=:notes,amount=:amount,version=version+1 WHERE id=:id",new MapSqlParameterSource("id",id).addValue("name",a.name().trim()).addValue("start",a.periodStart()).addValue("end",a.periodEnd()).addValue("notes",a.notes().trim()).addValue("amount",total));
  jdbc.update("DELETE FROM finance_budget_lines WHERE budget_id=:id",Map.of("id",id));
  for(var l:a.lines())jdbc.update("INSERT INTO finance_budget_lines(budget_id,category,amount) VALUES(:id,:category,:amount)",Map.of("id",id,"category",l.category(),"amount",l.amount()));
 }
 @Transactional public void editVendor(UUID id,VendorEdit input,String actor){lock("VENDOR",id,input.version());snapshot("VENDOR",id,"CONTACT_UPDATED",actor,input.note());jdbc.update("UPDATE finance_vendors SET name=:name,email=:email,phone=:phone,address=:address,version=version+1 WHERE id=:id",new MapSqlParameterSource("id",id).addValue("name",input.name().trim()).addValue("email",input.email()==null||input.email().isBlank()?null:input.email().trim()).addValue("phone",input.phone()).addValue("address",input.address()));}
 @Transactional(readOnly=true) public Map<String,Object> profile(User actor){return jdbc.queryForMap("SELECT u.id,u.full_name,u.email,u.role,u.is_active,u.created_at,coalesce(p.phone,'') AS phone,coalesce(p.designation,'') AS designation,coalesce(p.department,'') AS department,coalesce(p.office,'') AS office,coalesce(p.version,0) AS version FROM users u LEFT JOIN finance_user_profiles p ON p.user_id=u.id WHERE u.id=:id",Map.of("id",actor.getId()));}
 @Transactional public Map<String,Object> saveProfile(User actor,Profile input){
  jdbc.queryForList("SELECT id FROM users WHERE id=:id FOR UPDATE",Map.of("id",actor.getId()));var current=profile(actor);if(((Number)current.get("version")).longValue()!=input.version())throw new ConflictException("Profile changed. Reload before saving.");
  var p=new MapSqlParameterSource("id",actor.getId()).addValue("name",input.fullName().trim()).addValue("phone",input.phone().trim()).addValue("designation",input.designation().trim()).addValue("department",input.department().trim()).addValue("office",input.office().trim());
  jdbc.update("INSERT INTO finance_user_profiles(user_id,phone,designation,department,office) VALUES(:id,:phone,:designation,:department,:office) ON CONFLICT(user_id) DO UPDATE SET phone=:phone,designation=:designation,department=:department,office=:office,version=finance_user_profiles.version+1,updated_at=now()",p);
  jdbc.update("UPDATE users SET full_name=:name,updated_at=now() WHERE id=:id",p);audit.record(actor,"FINANCE_PROFILE_UPDATED","User",actor.getId());return profile(actor);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public List<Map<String,Object>> approvals(String actor){
  return jdbc.queryForList("""
   SELECT 'awards' AS kind,count(*) AS pending,count(*) FILTER(WHERE s.created_by<>:actor) AS reviewable FROM finance_scholarship_applications s WHERE s.status='VERIFIED' AND s.funding='INSTITUTION' AND NOT EXISTS(SELECT 1 FROM finance_award_decisions d WHERE d.application_id=s.id)
   UNION ALL SELECT 'refunds',count(*),count(*) FILTER(WHERE created_by<>:actor) FROM finance_refunds WHERE status='PENDING'
   UNION ALL SELECT 'operations',count(*),count(*) FILTER(WHERE created_by<>:actor) FROM finance_operating_entries WHERE status='PENDING' AND vendor_id IS NULL AND staff_user_id IS NULL
   UNION ALL SELECT 'budgets',count(*),count(*) FILTER(WHERE created_by<>:actor) FROM finance_budgets WHERE status='PENDING'
   UNION ALL SELECT 'disbursements',count(*),count(*) FILTER(WHERE created_by<>:actor AND NOT EXISTS(SELECT 1 FROM users u WHERE u.id=staff_user_id AND u.email=:actor)) FROM finance_operating_entries WHERE status='PENDING' AND (vendor_id IS NOT NULL OR staff_user_id IS NOT NULL)
   UNION ALL SELECT 'salaries',count(*),count(*) FILTER(WHERE created_by<>:actor AND NOT EXISTS(SELECT 1 FROM users u WHERE u.id=user_id AND u.email=:actor)) FROM finance_salary_structures WHERE status='PENDING'
   UNION ALL SELECT 'reversals',count(*),count(*) FILTER(WHERE requested_by<>:actor) FROM finance_award_reversals WHERE status='PENDING'
   """,Map.of("actor",actor));
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Map<String,Object> health(){return jdbc.queryForMap("""
  SELECT now() AS checked_at,
   (SELECT coalesce(sum(debit-credit),0) FROM finance_ledger_lines) AS journal_difference,
   (SELECT coalesce(sum(amount),0) FROM finance_fee_payments)+(SELECT coalesce(sum(amount),0) FROM finance_operating_entries WHERE direction='INCOME' AND status='SETTLED') AS source_inflow,
   (SELECT coalesce(sum(amount),0) FROM finance_refunds WHERE status='PAID')+(SELECT coalesce(sum(amount),0) FROM finance_operating_entries WHERE direction='EXPENSE' AND status='SETTLED') AS source_outflow,
   (SELECT coalesce(sum(amount) FILTER(WHERE debit_account IN ('1000','1010')),0) FROM finance_ledger_entries) AS ledger_inflow,
   (SELECT coalesce(sum(amount) FILTER(WHERE credit_account IN ('1000','1010')),0) FROM finance_ledger_entries) AS ledger_outflow,
   (SELECT count(*) FROM fees f WHERE f.amount_paid<0 OR f.concession_amount<0 OR f.amount_paid+f.concession_amount>f.amount) AS invalid_fee_balances,
   (SELECT count(*) FROM fees f WHERE amount_paid>0 AND NOT EXISTS(SELECT 1 FROM finance_fee_payment_allocations a WHERE a.fee_id=f.fee_id)) AS legacy_paid_charges
  """,Map.of());}
}
