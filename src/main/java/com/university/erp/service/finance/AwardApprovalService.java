package com.university.erp.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.erp.service.finance.FeeCollectionService.Allocation;
import com.university.erp.exception.ConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AwardApprovalService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
    public record Input(@NotNull UUID requestId,@NotBlank String outcome,@NotBlank @Size(max=2000) String note,
        @NotNull @Size(max=100) List<@NotNull @Valid Allocation> allocations,@Min(0) Long version){public Input(UUID requestId,String outcome,String note,List<Allocation> allocations){this(requestId,outcome,note,allocations,null);}}
    static BigDecimal validate(Input input,BigDecimal requested){
        if(!Set.of("POSTED","DECLINED").contains(input.outcome()))throw new IllegalArgumentException("Invalid approval outcome");
        FeeCollectionService.ordered(input.allocations());
        var total=input.allocations().stream().map(FeeCollectionService.Allocation::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(input.outcome().equals("DECLINED")&&!input.allocations().isEmpty())throw new IllegalArgumentException("Declined applications cannot allocate concessions");
        if(input.outcome().equals("POSTED")&&(total.signum()<=0||total.compareTo(requested)>0))throw new IllegalArgumentException("Allocate a positive award no greater than the requested amount");
        return total;
    }
    private String fingerprint(Input input){try{
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(List.of(input.outcome(),input.note().trim(),
            FeeCollectionService.ordered(input.allocations()).stream().map(l->List.of(l.feeId(),l.amount().stripTrailingZeros().toPlainString())).toList()))));
    }catch(Exception e){throw new IllegalArgumentException("Invalid allocation request");}}
    private static final String FROM=" FROM finance_scholarship_applications s JOIN admissions a ON a.admission_id=s.admission_id LEFT JOIN finance_award_decisions d ON d.application_id=s.id ";
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Map<String,Object>> list(String search,String stage,int page){
        if(!Set.of("PENDING","POSTED","DECLINED").contains(stage))throw new IllegalArgumentException("Invalid approval stage");
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("offset",page*20L).addValue("stage",stage);
        String where=" WHERE (s.status='VERIFIED' OR EXISTS(SELECT 1 FROM finance_award_reversals r WHERE r.decision_id=d.id AND r.status='APPROVED')) AND s.funding='INSTITUTION' AND (s.scheme ILIKE :search OR s.admission_id ILIKE :search OR a.first_name||' '||a.last_name ILIKE :search) AND "+(stage.equals("PENDING")?"d.id IS NULL":"d.outcome=:stage");
        long total=jdbc.queryForObject("SELECT count(*)"+FROM+where,p,Long.class);
        var rows=jdbc.queryForList("SELECT s.*,a.first_name||' '||a.last_name AS student_name,a.program,d.id AS decision_id,d.outcome,d.amount AS approved_amount,d.note AS decision_note,d.decided_by,d.created_at AS decided_at"+FROM+where+" ORDER BY s.created_at,s.id LIMIT 20 OFFSET :offset",p);
        return new PageImpl<>(rows,PageRequest.of(page,20),total);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> decision(UUID application){
        var rows=jdbc.queryForList("SELECT * FROM finance_award_decisions WHERE application_id=:app",Map.of("app",application));
        if(rows.isEmpty())throw new NoSuchElementException("Financial decision not found");
        var result=new LinkedHashMap<>(rows.get(0));
        result.put("reversals",jdbc.queryForList("SELECT * FROM finance_award_reversals WHERE decision_id=:id ORDER BY created_at DESC,id",Map.of("id",result.get("id"))));
        result.put("allocations",jdbc.queryForList("SELECT fee_id,fee_type,amount,balance_before,balance_after FROM finance_concession_allocations WHERE decision_id=:id ORDER BY fee_id",Map.of("id",result.get("id"))));return result;
    }
    @Transactional public Map<String,Object> decide(UUID application,Input input,String actor){
        String hash=fingerprint(input);
        var p=new MapSqlParameterSource("app",application).addValue("request",input.requestId()).addValue("hash",hash).addValue("actor",actor)
            .addValue("note",input.note().trim()).addValue("outcome",input.outcome()).addValue("id",UUID.randomUUID());
        // Lock the application first; all fee rows then use the same order as collections.
        var rows=jdbc.queryForList("SELECT * FROM finance_scholarship_applications WHERE id=:app FOR UPDATE",p);
        if(rows.isEmpty())throw new NoSuchElementException("Application not found");var app=rows.get(0);
        var existing=jdbc.queryForList("SELECT * FROM finance_award_decisions WHERE application_id=:app",p);
        if(!existing.isEmpty()){
            var previous=existing.get(0);
            if(input.requestId().equals(previous.get("request_id"))&&hash.equals(previous.get("request_hash"))&&actor.equals(previous.get("decided_by")))return decision(application);
            throw new ConflictException("A final financial decision already exists for this application");
        }
        if(input.version()!=null&&((Number)app.get("version")).longValue()!=input.version())throw new ConflictException("Application changed. Refresh before financial review.");
        if(actor.equals(app.get("created_by")))throw new ConflictException("You cannot approve or decline your own application");
        if(!app.get("status").equals("VERIFIED"))throw new ConflictException("Documents must be verified first");
        if(!app.get("funding").equals("INSTITUTION"))throw new ConflictException("External scholarship funding requires reconciliation, not a fee concession");
        BigDecimal total=validate(input,(BigDecimal)app.get("requested_amount"));p.addValue("total",total);
        if(input.outcome().equals("POSTED")){
            p.addValue("fees",input.allocations().stream().map(FeeCollectionService.Allocation::feeId).toList());
            var fees=jdbc.queryForList("SELECT * FROM fees WHERE fee_id IN(:fees) ORDER BY fee_id FOR UPDATE",p);
            if(fees.size()!=input.allocations().size())throw new NoSuchElementException("A selected fee charge no longer exists");
            Map<String,FeeCollectionService.Allocation> byId=new HashMap<>();input.allocations().forEach(l->byId.put(l.feeId(),l));
            // Insert and allocations remain in one transaction; any validation failure rolls back everything.
            insertDecision(p);
            LocalDate today=LocalDate.now(ZoneId.of(timeZone));
            for(var fee:fees){
                if(!app.get("admission_id").equals(fee.get("admission_id")))throw new IllegalArgumentException("Selected charges belong to a different student");
                BigDecimal reduction=byId.get(fee.get("fee_id")).amount(),paid=(BigDecimal)fee.get("amount_paid");
                BigDecimal net=((BigDecimal)fee.get("amount")).subtract((BigDecimal)fee.get("concession_amount"));
                BigDecimal after=FeeCollectionService.validateBalance(net,paid,reduction,(String)fee.get("status"));
                LocalDate due=((java.sql.Date)fee.get("due_date")).toLocalDate();
                String status=after.signum()==0?"PAID":due.isBefore(today)?"OVERDUE":paid.signum()>0?"PARTIAL":"PENDING";
                p.addValue("fee",fee.get("fee_id")).addValue("type",fee.get("fee_type")).addValue("reduction",reduction).addValue("before",net.subtract(paid)).addValue("after",after).addValue("status",status);
                jdbc.update("INSERT INTO finance_concession_allocations(decision_id,fee_id,fee_type,amount,balance_before,balance_after) VALUES(:id,:fee,:type,:reduction,:before,:after)",p);
                jdbc.update("UPDATE fees SET concession_amount=concession_amount+:reduction,status=:status,updated_at=now() WHERE fee_id=:fee",p);
            }
        }else insertDecision(p);
        return decision(application);
    }
    private void insertDecision(MapSqlParameterSource p){jdbc.update("""
        INSERT INTO finance_award_decisions(id,application_id,request_id,request_hash,outcome,amount,note,decided_by)
        VALUES(:id,:app,:request,:hash,:outcome,:total,:note,:actor)
        """,p);}
}
