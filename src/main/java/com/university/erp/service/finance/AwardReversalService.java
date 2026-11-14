package com.university.erp.service.finance;

import com.university.erp.exception.ConflictException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.util.*;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class AwardReversalService {
 private final NamedParameterJdbcTemplate jdbc;
 public record Request(@NotNull UUID requestId,@NotBlank @Size(max=2000) String reason){}
 public record Review(@NotBlank String status,@NotBlank @Size(max=2000) String note){}
 static void validateReviewer(String status,String requester,String actor){
  if(!Set.of("APPROVED","REJECTED").contains(status))throw new IllegalArgumentException("Choose approve or reject reversal");
  if(requester.equals(actor))throw new ConflictException("Another finance staff member must review this reversal");
 }
 private static final String FROM=" FROM finance_award_reversals r JOIN finance_award_decisions d ON d.id=r.decision_id JOIN finance_scholarship_applications s ON s.id=d.application_id JOIN admissions a ON a.admission_id=s.admission_id ";
 private static final String SELECT="SELECT r.*,d.application_id,d.amount,s.scheme,s.admission_id,a.first_name||' '||a.last_name AS student_name"+FROM;
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page<Map<String,Object>> list(String search,String status,int page){
  if(!Set.of("","PENDING","APPROVED","REJECTED").contains(status))throw new IllegalArgumentException("Invalid reversal status");
  var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("status",status).addValue("offset",page*20L);
  String where=" WHERE (:status='' OR r.status=:status) AND (s.scheme ILIKE :search OR s.admission_id ILIKE :search OR a.first_name||' '||a.last_name ILIKE :search)";
  long count=jdbc.queryForObject("SELECT count(*)"+FROM+where,p,Long.class);
  return new PageImpl<>(jdbc.queryForList(SELECT+where+" ORDER BY r.created_at DESC,r.id LIMIT 20 OFFSET :offset",p),PageRequest.of(page,20),count);
 }
 private Map<String,Object> get(UUID id){return jdbc.queryForMap(SELECT+" WHERE r.id=:id",Map.of("id",id));}
 @Transactional public Map<String,Object> request(UUID application,Request input,String actor){
  var p=new MapSqlParameterSource("app",application).addValue("request",input.requestId()).addValue("actor",actor).addValue("reason",input.reason().trim()).addValue("id",UUID.randomUUID());
  var apps=jdbc.queryForList("SELECT id FROM finance_scholarship_applications WHERE id=:app FOR UPDATE",p);
  if(apps.isEmpty())throw new NoSuchElementException("Application not found");
  var previous=jdbc.queryForList("SELECT r.*,d.application_id FROM finance_award_reversals r JOIN finance_award_decisions d ON d.id=r.decision_id WHERE r.request_id=:request",p);
  if(!previous.isEmpty()){
   var r=previous.get(0);if(application.equals(r.get("application_id"))&&actor.equals(r.get("requested_by"))&&input.reason().trim().equals(r.get("reason")))return get((UUID)r.get("id"));
   throw new ConflictException("Request ID already used for another reversal");
  }
  var decisions=jdbc.queryForList("SELECT * FROM finance_award_decisions WHERE application_id=:app AND outcome='POSTED'",p);
  if(decisions.isEmpty())throw new ConflictException("Only a posted institution-funded award can be reversed");
  p.addValue("decision",decisions.get(0).get("id"));
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM finance_award_reversals WHERE decision_id=:decision AND status IN ('PENDING','APPROVED'))",p,Boolean.class)))throw new ConflictException("This award already has a pending or approved reversal");
  jdbc.update("INSERT INTO finance_award_reversals(id,request_id,decision_id,reason,requested_by) VALUES(:id,:request,:decision,:reason,:actor)",p);return get((UUID)p.getValue("id"));
 }
 @Transactional public Map<String,Object> review(UUID id,Review input,String actor){
  var p=new MapSqlParameterSource("id",id).addValue("actor",actor).addValue("status",input.status()).addValue("note",input.note().trim());
  var rows=jdbc.queryForList("SELECT * FROM finance_award_reversals WHERE id=:id FOR UPDATE",p);
  if(rows.isEmpty())throw new NoSuchElementException("Reversal not found");var r=rows.get(0);
  validateReviewer(input.status(),(String)r.get("requested_by"),actor);
  if(!r.get("status").equals("PENDING")){
   if(r.get("status").equals(input.status())&&actor.equals(r.get("reviewed_by"))&&input.note().trim().equals(r.get("review_note")))return get(id);
   throw new ConflictException("This reversal has already been reviewed");
  }
  p.addValue("decision",r.get("decision_id"));
  if(input.status().equals("APPROVED")){
   // Same fee lock ordering as collections and award posting; preserve all cash receipts.
   var fees=jdbc.queryForList("SELECT f.*,c.amount AS reversal_amount FROM fees f JOIN finance_concession_allocations c ON c.fee_id=f.fee_id WHERE c.decision_id=:decision ORDER BY f.fee_id FOR UPDATE OF f",p);
   if(fees.isEmpty())throw new ConflictException("Posted award allocations are missing");
   for(var f:fees){
    BigDecimal amount=(BigDecimal)f.get("reversal_amount"),concession=(BigDecimal)f.get("concession_amount");
    if(concession.compareTo(amount)<0||"WAIVED".equals(f.get("status")))throw new ConflictException("A linked charge needs reconciliation before reversal");
    p.addValue("fee",f.get("fee_id")).addValue("amount",amount);
    jdbc.update("UPDATE fees SET concession_amount=concession_amount-:amount,status=CASE WHEN amount-concession_amount+:amount-amount_paid<=0 THEN 'PAID' WHEN due_date<(now() AT TIME ZONE 'Asia/Kolkata')::date THEN 'OVERDUE' WHEN amount_paid>0 THEN 'PARTIAL' ELSE 'PENDING' END,updated_at=now() WHERE fee_id=:fee",p);
   }
   // Free the old application key for a corrected submission; keep original approval unchanged.
   jdbc.update("UPDATE finance_scholarship_applications SET status='CANCELLED',version=version+1 WHERE id=(SELECT application_id FROM finance_award_decisions WHERE id=:decision)",p);
  }
  jdbc.update("UPDATE finance_award_reversals SET status=:status,reviewed_by=:actor,review_note=:note,reviewed_at=now() WHERE id=:id",p);return get(id);
 }
}
