package com.university.erp.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.erp.exception.ConflictException;
import com.university.erp.service.finance.FeeCollectionService.Allocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class FeeRefundService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
    public record Input(@NotNull UUID requestId,@NotNull UUID paymentId,@NotBlank @Size(max=2000) String reason,
        @NotEmpty @Size(max=100) List<@NotNull @Valid Allocation> allocations,
        @Size(max=40) String category,@Size(max=30) String source,@Size(max=250) String evidenceReference,LocalDate targetDate){
        public Input(UUID requestId,UUID paymentId,String reason,List<Allocation> allocations){this(requestId,paymentId,reason,allocations,null,null,null,null);}
    }
    public record Edit(@Min(0) long version,@NotNull @Valid Input application,@NotBlank @Size(max=2000) String note){}
    public record Action(@Min(0) long version,@NotBlank @Size(max=40) String action,@NotBlank @Size(max=2000) String note){}
    public record Review(@NotBlank String status,@NotBlank @Size(max=2000) String note,@Min(0) Long version){public Review(String status,String note){this(status,note,null);}}
    public record Payout(@NotNull UUID requestId,@NotBlank String method,@NotBlank @Size(max=100) String reference,
        @NotNull LocalDate paidDate,@NotNull @AssertTrue Boolean verified,@Min(0) Long version){public Payout(UUID requestId,String method,String reference,LocalDate paidDate,Boolean verified){this(requestId,method,reference,paidDate,verified,null);}}
    static void validateReview(String current,String creator,String actor,Review input){
        if(!Set.of("APPROVED","REJECTED","CANCELLED").contains(input.status()))throw new IllegalArgumentException("Invalid refund decision");
        if(!current.equals("PENDING"))throw new ConflictException("Only pending refunds can be reviewed or withdrawn");
        if(input.status().equals("CANCELLED")){
            if(!creator.equals(actor))throw new ConflictException("Only the requester can withdraw the refund");
        }else if(creator.equals(actor))throw new ConflictException("Another finance staff member must approve or reject your refund");
    }
    static BigDecimal refundable(BigDecimal original,BigDecimal reserved,BigDecimal requested){
        if(requested.signum()<=0)throw new IllegalArgumentException("Refund amount must be positive");
        if(requested.compareTo(original.subtract(reserved))>0)throw new ConflictException("Refund exceeds the receipt's available allocation. Refresh the receipt.");
        return original.subtract(reserved).subtract(requested);
    }
    private String hash(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));}catch(Exception e){throw new IllegalArgumentException("Invalid refund request");}}
    private static final String FROM=" FROM finance_refunds r JOIN finance_fee_payments p ON p.id=r.payment_id ";
    private static final String SELECT="SELECT r.*,'RF-'||lpad(r.sequence_number::text,greatest(6,length(r.sequence_number::text)),'0') AS refund_number,p.receipt_number,p.admission_id,p.student_name,p.program";
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Map<String,Object>> list(String search,String status,int page){
        if(!Set.of("","PENDING","APPROVED","REJECTED","CANCELLED","PAID","PROCESSING","FAILED","READY","AWAITING_VERIFICATION").contains(status))throw new IllegalArgumentException("Invalid refund status");
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("status",status).addValue("offset",page*20L);
        String where=" WHERE (:status='' OR r.status=:status OR (r.status='APPROVED' AND r.payout_progress=:status) OR (:status='AWAITING_VERIFICATION' AND r.status='PENDING' AND r.verification_status<>'VERIFIED')) AND (p.receipt_number ILIKE :search OR p.admission_id ILIKE :search OR p.student_name ILIKE :search OR r.id::text ILIKE :search OR 'RF-'||lpad(r.sequence_number::text,greatest(6,length(r.sequence_number::text)),'0') ILIKE :search) ";
        long total=jdbc.queryForObject("SELECT count(*)"+FROM+where,p,Long.class);
        return new PageImpl<>(jdbc.queryForList(SELECT+FROM+where+" ORDER BY r.created_at DESC,r.id LIMIT 20 OFFSET :offset",p),PageRequest.of(page,20),total);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> summary(String search){
        return jdbc.queryForList("SELECT r.status,count(*) AS count,coalesce(sum(r.amount),0) AS amount,count(*) FILTER(WHERE r.status='APPROVED' AND r.payout_progress='FAILED') AS failed,count(*) FILTER(WHERE r.status IN ('PENDING','APPROVED') AND r.target_date<(now() AT TIME ZONE 'Asia/Kolkata')::date) AS overdue"+FROM+" WHERE p.receipt_number ILIKE :search OR p.admission_id ILIKE :search OR p.student_name ILIKE :search OR r.id::text ILIKE :search OR 'RF-'||lpad(r.sequence_number::text,greatest(6,length(r.sequence_number::text)),'0') ILIKE :search GROUP BY r.status",Map.of("search","%"+search.trim()+"%"));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(UUID id){
        var rows=jdbc.queryForList(SELECT+FROM+" WHERE r.id=:id",Map.of("id",id));
        if(rows.isEmpty())throw new NoSuchElementException("Refund not found");
        var result=new LinkedHashMap<>(rows.get(0));result.remove("request_hash");result.remove("payout_hash");
        result.put("events",jdbc.queryForList("SELECT action,actor,note,created_at FROM finance_refund_events WHERE refund_id=:id ORDER BY id DESC LIMIT 100",Map.of("id",id)));
        result.put("allocations",jdbc.queryForList("SELECT fee_id,fee_type,amount,paid_before,paid_after FROM finance_refund_allocations WHERE refund_id=:id ORDER BY fee_id",Map.of("id",id)));return result;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> receipt(UUID payment){
        var receipts=jdbc.queryForList("SELECT id,receipt_number,admission_id,student_name,program,amount,paid_date FROM finance_fee_payments WHERE id=:id",Map.of("id",payment));
        if(receipts.isEmpty())throw new NoSuchElementException("Receipt not found");var result=new LinkedHashMap<>(receipts.get(0));
        // At most 100 receipt lines. Pending/approved requests reserve capacity until rejected/withdrawn.
        result.put("allocations",jdbc.queryForList("""
            SELECT a.fee_id,a.fee_type,a.amount AS original_amount,
              coalesce((SELECT sum(l.amount) FROM finance_refund_allocations l JOIN finance_refunds r ON r.id=l.refund_id
                WHERE r.payment_id=a.payment_id AND l.fee_id=a.fee_id AND r.status IN('PENDING','APPROVED','PAID')),0) AS reserved_amount
            FROM finance_fee_payment_allocations a WHERE a.payment_id=:id ORDER BY a.fee_id
            """,Map.of("id",payment)));return result;
    }
    private static String category(Input input){String value=input.category()==null?"OTHER":input.category();if(!Set.of("OTHER","ADMISSION_CANCELLATION","EXCESS_PAYMENT","DUPLICATE_PAYMENT","SERVICE_CANCELLED").contains(value))throw new IllegalArgumentException("Invalid refund category");return value;}
    private static String source(Input input){String value=input.source()==null?"ACCOUNTS":input.source();if(!Set.of("ACCOUNTS","STUDENT_OFFLINE").contains(value))throw new IllegalArgumentException("Choose Accounts or student offline request");return value;}
    private void event(UUID id,String action,String actor,String note){jdbc.update("INSERT INTO finance_refund_events(refund_id,action,actor,note) VALUES(:id,:action,:actor,:note)",Map.of("id",id,"action",action,"actor",actor,"note",note.trim()));}
    private String fingerprint(Input input,List<Allocation> lines){return hash(List.of(input.paymentId(),input.reason().trim(),lines.stream().map(l->List.of(l.feeId(),l.amount().stripTrailingZeros().toPlainString())).toList(),category(input),source(input),input.evidenceReference()==null?"":input.evidenceReference().trim(),input.targetDate()==null?"":input.targetDate().toString()));}
    @Transactional public Map<String,Object> create(Input input,String actor){
        var lines=FeeCollectionService.ordered(input.allocations());
        String fingerprint=fingerprint(input,lines);
        var p=new MapSqlParameterSource("request",input.requestId()).addValue("hash",fingerprint).addValue("payment",input.paymentId()).addValue("actor",actor).addValue("reason",input.reason().trim()).addValue("id",UUID.randomUUID());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:request AS text),0))",p);
        var previous=jdbc.queryForList("SELECT * FROM finance_refunds WHERE request_id=:request",p);
        if(!previous.isEmpty()){
            var old=previous.get(0);if(!fingerprint.equals(old.get("request_hash"))||!actor.equals(old.get("created_by")))throw new ConflictException("Request ID already used for another refund");return detail((UUID)old.get("id"));
        }
        // Serialize reservations on the original receipt, preventing multiple requests from over-refunding it.
        if(jdbc.queryForList("SELECT id FROM finance_fee_payments WHERE id=:payment FOR UPDATE",p).isEmpty())throw new NoSuchElementException("Receipt not found");
        var available=receipt(input.paymentId());
        @SuppressWarnings("unchecked") var allocations=(List<Map<String,Object>>)available.get("allocations");
        var byId=new HashMap<String,Map<String,Object>>();allocations.forEach(l->byId.put((String)l.get("fee_id"),l));
        BigDecimal total=lines.stream().map(Allocation::amount).reduce(BigDecimal.ZERO,BigDecimal::add);p.addValue("amount",total);
        for(var line:lines){var original=byId.get(line.feeId());if(original==null)throw new IllegalArgumentException("Selected charge is not part of this receipt");refundable((BigDecimal)original.get("original_amount"),(BigDecimal)original.get("reserved_amount"),line.amount());}
        jdbc.update("INSERT INTO finance_refunds(id,request_id,request_hash,payment_id,amount,reason,created_by) VALUES(:id,:request,:hash,:payment,:amount,:reason,:actor)",p);
        for(var line:lines){p.addValue("fee",line.feeId()).addValue("type",byId.get(line.feeId()).get("fee_type")).addValue("lineAmount",line.amount());jdbc.update("INSERT INTO finance_refund_allocations(refund_id,fee_id,fee_type,amount) VALUES(:id,:fee,:type,:lineAmount)",p);}
        p.addValue("category",category(input)).addValue("source",source(input)).addValue("evidence",input.evidenceReference()==null?null:input.evidenceReference().trim()).addValue("target",input.targetDate());
        jdbc.update("UPDATE finance_refunds SET category=:category,request_source=:source,evidence_reference=:evidence,target_date=:target WHERE id=:id",p);
        event((UUID)p.getValue("id"),"REQUESTED",actor,input.reason());
        return detail((UUID)p.getValue("id"));
    }
    @Transactional public Map<String,Object> edit(UUID id,Edit input,String actor){
        var p=new MapSqlParameterSource("id",id);var rows=jdbc.queryForList("SELECT * FROM finance_refunds WHERE id=:id FOR UPDATE",p);if(rows.isEmpty())throw new NoSuchElementException("Refund not found");var r=rows.get(0);
        if(!actor.equals(r.get("created_by"))||!"PENDING".equals(r.get("status")))throw new ConflictException("Only the requester can edit a pending refund");
        if(((Number)r.get("version")).longValue()!=input.version())throw new ConflictException("Refund changed. Reload before editing.");
        var a=input.application();if(!a.paymentId().equals(r.get("payment_id")))throw new IllegalArgumentException("Original receipt cannot be changed");
        var lines=FeeCollectionService.ordered(a.allocations());p.addValue("payment",a.paymentId());jdbc.queryForList("SELECT id FROM finance_fee_payments WHERE id=:payment FOR UPDATE",p);
        var original=jdbc.queryForList("SELECT a.fee_id,a.fee_type,a.amount,coalesce((SELECT sum(l.amount) FROM finance_refund_allocations l JOIN finance_refunds r ON r.id=l.refund_id WHERE r.payment_id=a.payment_id AND r.id<>:id AND l.fee_id=a.fee_id AND r.status IN ('PENDING','APPROVED','PAID')),0) AS reserved FROM finance_fee_payment_allocations a WHERE a.payment_id=:payment",p);
        var byId=new HashMap<String,Map<String,Object>>();original.forEach(l->byId.put((String)l.get("fee_id"),l));
        for(var line:lines){var f=byId.get(line.feeId());if(f==null)throw new IllegalArgumentException("Charge does not belong to original receipt");refundable((BigDecimal)f.get("amount"),(BigDecimal)f.get("reserved"),line.amount());}
        p.addValue("actor",actor).addValue("note",input.note().trim());jdbc.update("INSERT INTO finance_refund_events(refund_id,action,actor,note,previous_values) SELECT id,'EDITED',:actor,:note,to_jsonb(r)||jsonb_build_object('allocations',(SELECT jsonb_agg(to_jsonb(l)) FROM finance_refund_allocations l WHERE l.refund_id=r.id)) FROM finance_refunds r WHERE id=:id",p);
        jdbc.update("DELETE FROM finance_refund_allocations WHERE refund_id=:id",p);
        for(var line:lines){p.addValue("fee",line.feeId()).addValue("type",byId.get(line.feeId()).get("fee_type")).addValue("lineAmount",line.amount());jdbc.update("INSERT INTO finance_refund_allocations(refund_id,fee_id,fee_type,amount) VALUES(:id,:fee,:type,:lineAmount)",p);}
        p.addValue("reason",a.reason().trim()).addValue("category",category(a)).addValue("source",source(a)).addValue("evidence",a.evidenceReference()==null?null:a.evidenceReference().trim()).addValue("target",a.targetDate()).addValue("amount",lines.stream().map(Allocation::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
        jdbc.update("UPDATE finance_refunds SET reason=:reason,category=:category,request_source=:source,evidence_reference=:evidence,target_date=:target,amount=:amount,version=version+1,verification_status='PENDING',verified_by=NULL,verified_at=NULL,verification_note=NULL WHERE id=:id",p);return detail(id);
    }
    @Transactional public Map<String,Object> action(UUID id,Action input,String actor){
        var p=new MapSqlParameterSource("id",id).addValue("actor",actor).addValue("note",input.note().trim());var rows=jdbc.queryForList("SELECT * FROM finance_refunds WHERE id=:id FOR UPDATE",p);if(rows.isEmpty())throw new NoSuchElementException("Refund not found");var r=rows.get(0);
        if(((Number)r.get("version")).longValue()!=input.version())throw new ConflictException("Refund changed. Reload before updating.");
        jdbc.update("INSERT INTO finance_refund_events(refund_id,action,actor,note,previous_values) SELECT id,:action,:actor,:note,to_jsonb(r) FROM finance_refunds r WHERE id=:id",p.addValue("action",input.action()));
        switch(input.action()){
            case "VERIFY" -> {if(!"PENDING".equals(r.get("status")))throw new ConflictException("Only pending refunds can be verified");if(actor.equals(r.get("created_by")))throw new ConflictException("Another finance staff member must verify the request");jdbc.update("UPDATE finance_refunds SET verification_status='VERIFIED',verified_by=:actor,verified_at=now(),verification_note=:note,version=version+1 WHERE id=:id",p);}
            case "RETURN" -> {if(!"APPROVED".equals(r.get("status"))||"PROCESSING".equals(r.get("payout_progress"))||actor.equals(r.get("created_by")))throw new ConflictException("A separate reviewer can return an approved, unpaid refund only when payout is not processing");jdbc.update("UPDATE finance_refunds SET status='PENDING',verification_status='PENDING',verified_by=NULL,verified_at=NULL,verification_note=NULL,payout_progress='READY',version=version+1,reviewed_by=NULL,reviewed_at=NULL,review_note=NULL WHERE id=:id",p);}
            case "PROCESSING","FAILED","READY" -> {if(!"APPROVED".equals(r.get("status")))throw new ConflictException("Only approved unpaid refunds have payout progress");p.addValue("progress",input.action());jdbc.update("UPDATE finance_refunds SET payout_progress=:progress,version=version+1 WHERE id=:id",p);}
            case "NOTE" -> {jdbc.update("UPDATE finance_refunds SET version=version+1 WHERE id=:id",p);}
            default -> throw new IllegalArgumentException("Invalid refund workflow action");
        }
        return detail(id);
    }
    @Transactional public Map<String,Object> review(UUID id,Review input,String actor){
        var rows=jdbc.queryForList("SELECT * FROM finance_refunds WHERE id=:id FOR UPDATE",Map.of("id",id));if(rows.isEmpty())throw new NoSuchElementException("Refund not found");var r=rows.get(0);
        if(input.status().equals(r.get("status"))&&actor.equals(r.get("reviewed_by"))&&input.note().trim().equals(r.get("review_note")))return detail(id);
        if(input.version()!=null&&((Number)r.get("version")).longValue()!=input.version())throw new ConflictException("Refund changed. Reload before review.");
        if(input.status().equals("APPROVED")&&!"VERIFIED".equals(r.get("verification_status")))throw new ConflictException("Verify supporting documents and eligibility before approval");
        validateReview((String)r.get("status"),(String)r.get("created_by"),actor,input);
        jdbc.update("UPDATE finance_refunds SET version=version+1,status=:status,reviewed_by=:actor,reviewed_at=now(),review_note=:note WHERE id=:id",Map.of("id",id,"status",input.status(),"actor",actor,"note",input.note().trim()));event(id,input.status(),actor,input.note());return detail(id);
    }
    @Transactional public Map<String,Object> payout(UUID id,Payout input,String actor){
        String method=FeeCollectionService.method(input.method()),reference=input.reference().trim().toUpperCase(Locale.ROOT);
        String fingerprint=hash(List.of(method,reference,input.paidDate()));
        var p=new MapSqlParameterSource("id",id).addValue("request",input.requestId()).addValue("hash",fingerprint).addValue("actor",actor).addValue("method",method).addValue("reference",reference).addValue("date",java.sql.Date.valueOf(input.paidDate()));
        var rows=jdbc.queryForList("SELECT r.*,p.paid_date AS original_date FROM finance_refunds r JOIN finance_fee_payments p ON p.id=r.payment_id WHERE r.id=:id FOR UPDATE OF r",p);
        if(rows.isEmpty())throw new NoSuchElementException("Refund not found");var r=rows.get(0);
        if(r.get("status").equals("PAID")){
            if(input.requestId().equals(r.get("payout_request_id"))&&fingerprint.equals(r.get("payout_hash"))&&actor.equals(r.get("paid_by")))return detail(id);
            throw new ConflictException("This refund already has a recorded payout");
        }
        if(input.version()!=null&&((Number)r.get("version")).longValue()!=input.version())throw new ConflictException("Refund changed. Reload before recording payout.");
        if(!r.get("status").equals("APPROVED"))throw new ConflictException("Approve the refund before recording a payout");
        if("FAILED".equals(r.get("payout_progress")))throw new ConflictException("Resolve the failed payout and mark ready or processing before recording payment");
        LocalDate today=LocalDate.now(ZoneId.of(timeZone));
        if(input.paidDate().isAfter(today)||input.paidDate().isBefore(((java.sql.Date)r.get("original_date")).toLocalDate()))throw new IllegalArgumentException("Payout date must be between the original payment date and today");
        var lines=jdbc.queryForList("SELECT f.*,l.amount AS refund_amount FROM finance_refund_allocations l JOIN fees f ON f.fee_id=l.fee_id WHERE l.refund_id=:id ORDER BY f.fee_id FOR UPDATE OF f",p);
        if(lines.isEmpty()||lines.stream().map(f->(BigDecimal)f.get("refund_amount")).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo((BigDecimal)r.get("amount"))!=0)throw new ConflictException("Refund allocations need reconciliation before payout");
        for(var fee:lines){
            BigDecimal paid=(BigDecimal)fee.get("amount_paid"),amount=(BigDecimal)fee.get("refund_amount"),after=paid.subtract(amount);
            if(after.signum()<0)throw new ConflictException("Refund exceeds the fee's current net paid balance. Reconcile this charge first.");
            BigDecimal balance=((BigDecimal)fee.get("amount")).subtract((BigDecimal)fee.get("concession_amount")).subtract(after);
            String status=fee.get("status").equals("WAIVED")?"WAIVED":balance.signum()<=0?"PAID":((java.sql.Date)fee.get("due_date")).toLocalDate().isBefore(today)?"OVERDUE":after.signum()>0?"PARTIAL":"PENDING";
            p.addValue("fee",fee.get("fee_id")).addValue("before",paid).addValue("after",after).addValue("status",status);
            jdbc.update("UPDATE fees SET amount_paid=:after,status=:status,updated_at=now() WHERE fee_id=:fee",p);
            jdbc.update("UPDATE finance_refund_allocations SET paid_before=:before,paid_after=:after WHERE refund_id=:id AND fee_id=:fee",p);
        }
        jdbc.update("UPDATE finance_refunds SET status='PAID',version=version+1,payout_progress='COMPLETED',payout_request_id=:request,payout_hash=:hash,payout_method=:method,payout_reference=:reference,payout_date=:date,paid_by=:actor,paid_at=now() WHERE id=:id",p);
        event(id,"PAID",actor,"Verified payout recorded: "+reference);return detail(id);
    }
}
