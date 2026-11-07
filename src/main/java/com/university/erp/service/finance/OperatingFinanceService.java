package com.university.erp.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.erp.exception.ConflictException;
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
public class OperatingFinanceService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
    public record Input(@NotNull UUID requestId,@NotBlank String direction,@NotBlank String category,
        @NotBlank @Size(max=150) String counterparty,@NotBlank @Size(max=150) String documentReference,
        @NotNull LocalDate documentDate,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
        @NotBlank @Size(max=2000) String description){}
    public record Review(@NotBlank String status,@NotBlank @Size(max=2000) String note){}
    public record Settlement(@NotNull UUID requestId,@NotBlank String method,@NotBlank @Size(max=100) String reference,
        @NotNull LocalDate paidDate,@NotNull @AssertTrue Boolean verified){}
    public record Totals(long records,BigDecimal settledIncome,BigDecimal settledExpenses,BigDecimal netCash,
        BigDecimal pendingAmount,BigDecimal approvedAmount){}
    public record Worklist(Totals totals,Page<Map<String,Object>> entries,LocalDate asOf){}
    public static final Map<String,List<String>> CATEGORIES=Map.of(
        "INCOME",List.of("DONATION","GRANT","EVENT","INTEREST","OTHER_INCOME"),
        "EXPENSE",List.of("UTILITIES","MAINTENANCE","ADMINISTRATION","EVENT","OTHER_EXPENSE"));
    private LocalDate today(){return LocalDate.now(ZoneId.of(timeZone));}
    static void validate(Input input,LocalDate today){
        if(!CATEGORIES.containsKey(input.direction())||!CATEGORIES.get(input.direction()).contains(input.category()))throw new IllegalArgumentException("Choose a valid non-fee income or operating expense category");
        if(input.documentDate().isAfter(today))throw new IllegalArgumentException("Document date cannot be in the future");
    }
    static void validateReview(String current,String creator,String actor,Review review){
        if(!Set.of("APPROVED","REJECTED","CANCELLED").contains(review.status()))throw new IllegalArgumentException("Invalid operating entry decision");
        if(!current.equals("PENDING"))throw new ConflictException("Only pending entries can be reviewed or withdrawn");
        if(review.status().equals("CANCELLED")){
            if(!creator.equals(actor))throw new ConflictException("Only the creator can withdraw this entry");
        }else if(creator.equals(actor))throw new ConflictException("Another finance staff member must approve or reject your entry");
    }
    static void validateSettlement(String status,LocalDate documentDate,LocalDate paidDate,LocalDate today){
        if(!status.equals("APPROVED"))throw new ConflictException("Approve this entry before recording a settlement");
        if(paidDate.isBefore(documentDate)||paidDate.isAfter(today))throw new IllegalArgumentException("Settlement date must be between the document date and today");
    }
    private String hash(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));}catch(Exception e){throw new IllegalArgumentException("Invalid operating entry");}}
    private Map<String,Object> publicRow(Map<String,Object> row){var result=new LinkedHashMap<>(row);result.remove("request_hash");result.remove("settlement_hash");return result;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Worklist list(String search,String direction,String status,LocalDate from,LocalDate until,int page){return listCategory(search,direction,status,from,until,page,"");}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Worklist vendorPayments(String search,String status,int page){return listCategory(search,"EXPENSE",status,null,null,page,"VENDOR");}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Worklist payrollPayments(String search,String status,int page){return listCategory(search,"EXPENSE",status,null,null,page,"PAYROLL");}
    private Worklist listCategory(String search,String direction,String status,LocalDate from,LocalDate until,int page,String dedicated){
        if(!Set.of("","INCOME","EXPENSE").contains(direction))throw new IllegalArgumentException("Invalid direction");
        if(!Set.of("","PENDING","APPROVED","REJECTED","CANCELLED","SETTLED").contains(status))throw new IllegalArgumentException("Invalid entry status");
        if(from!=null&&until!=null&&until.isBefore(from))throw new IllegalArgumentException("End date must be on or after start date");
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("direction",direction).addValue("status",status).addValue("offset",page*20L);
        String where=" WHERE "+(dedicated.equals("VENDOR")?"vendor_id IS NOT NULL":dedicated.equals("PAYROLL")?"staff_user_id IS NOT NULL":"vendor_id IS NULL AND staff_user_id IS NULL")+" AND (:direction='' OR direction=:direction) AND (:status='' OR status=:status) AND (counterparty ILIKE :search OR document_reference ILIKE :search OR coalesce(settlement_reference,'') ILIKE :search) ";
        if(from!=null){where+=" AND document_date>=:from";p.addValue("from",java.sql.Date.valueOf(from));}
        if(until!=null){where+=" AND document_date<=:until";p.addValue("until",java.sql.Date.valueOf(until));}
        var totals=jdbc.queryForObject("""
            SELECT count(*),coalesce(sum(amount) FILTER(WHERE status='SETTLED' AND direction='INCOME'),0),
              coalesce(sum(amount) FILTER(WHERE status='SETTLED' AND direction='EXPENSE'),0),
              coalesce(sum(amount) FILTER(WHERE status='PENDING'),0),coalesce(sum(amount) FILTER(WHERE status='APPROVED'),0)
            FROM finance_operating_entries
            """+where,p,(rs,n)->new Totals(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBigDecimal(2).subtract(rs.getBigDecimal(3)),rs.getBigDecimal(4),rs.getBigDecimal(5)));
        var rows=jdbc.queryForList("SELECT * FROM finance_operating_entries"+where+" ORDER BY document_date DESC,created_at DESC,id LIMIT 20 OFFSET :offset",p).stream().map(this::publicRow).toList();
        return new Worklist(totals,new PageImpl<>(rows,PageRequest.of(page,20),totals.records()),today());
    }
    @Transactional(readOnly=true) public Map<String,Object> detail(UUID id){
        var rows=jdbc.queryForList("SELECT * FROM finance_operating_entries WHERE id=:id",Map.of("id",id));
        if(rows.isEmpty())throw new NoSuchElementException("Operating entry not found");return publicRow(rows.get(0));
    }
    @Transactional public Map<String,Object> create(Input input,String actor){
        validate(input,today());
        return persist(input,actor);
    }
    @Transactional public Map<String,Object> createDisbursement(Input input,String actor){
        if(!input.direction().equals("EXPENSE")||!Set.of("VENDOR_PAYMENT","PAYROLL").contains(input.category()))throw new IllegalArgumentException("Invalid vendor payment category");
        if(input.documentDate().isAfter(today()))throw new IllegalArgumentException("Invoice date cannot be in the future");
        return persist(input,actor);
    }
    private Map<String,Object> persist(Input input,String actor){
        String fingerprint=hash(List.of(input.direction(),input.category(),input.counterparty().trim(),input.documentReference().trim(),input.documentDate(),input.amount().stripTrailingZeros().toPlainString(),input.description().trim()));
        var p=new MapSqlParameterSource("request",input.requestId()).addValue("hash",fingerprint).addValue("actor",actor).addValue("id",UUID.randomUUID())
            .addValue("direction",input.direction()).addValue("category",input.category()).addValue("party",input.counterparty().trim()).addValue("reference",input.documentReference().trim())
            .addValue("date",java.sql.Date.valueOf(input.documentDate())).addValue("amount",input.amount()).addValue("description",input.description().trim());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:request AS text),0))",p);
        var previous=jdbc.queryForList("SELECT * FROM finance_operating_entries WHERE request_id=:request",p);
        if(!previous.isEmpty()){
            var old=previous.get(0);if(!fingerprint.equals(old.get("request_hash"))||!actor.equals(old.get("created_by")))throw new ConflictException("Request ID already used for another entry");return detail((UUID)old.get("id"));
        }
        int inserted=jdbc.update("""
            INSERT INTO finance_operating_entries(id,request_id,request_hash,direction,category,counterparty,document_reference,document_date,amount,description,created_by)
            VALUES(:id,:request,:hash,:direction,:category,:party,:reference,:date,:amount,:description,:actor) ON CONFLICT DO NOTHING
            """,p);
        if(inserted==0)throw new ConflictException("An active entry already exists for this direction, counterparty and document reference");return detail((UUID)p.getValue("id"));
    }
    @Transactional public Map<String,Object> review(UUID id,Review input,String actor){
        var rows=jdbc.queryForList("SELECT * FROM finance_operating_entries WHERE id=:id FOR UPDATE",Map.of("id",id));if(rows.isEmpty())throw new NoSuchElementException("Operating entry not found");var entry=rows.get(0);
        if(entry.get("staff_user_id")!=null&&!input.status().equals("CANCELLED")&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=:staff AND email=:actor)",Map.of("staff",entry.get("staff_user_id"),"actor",actor),Boolean.class)))throw new ConflictException("The payroll beneficiary cannot review their own payout");
        if(input.status().equals(entry.get("status"))&&actor.equals(entry.get("reviewed_by"))&&input.note().trim().equals(entry.get("review_note")))return detail(id);
        validateReview((String)entry.get("status"),(String)entry.get("created_by"),actor,input);
        jdbc.update("UPDATE finance_operating_entries SET status=:status,reviewed_by=:actor,reviewed_at=now(),review_note=:note WHERE id=:id",Map.of("id",id,"status",input.status(),"actor",actor,"note",input.note().trim()));return detail(id);
    }
    @Transactional public Map<String,Object> settle(UUID id,Settlement input,String actor){
        String method=FeeCollectionService.method(input.method()),reference=input.reference().trim().toUpperCase(Locale.ROOT);
        String fingerprint=hash(List.of(method,reference,input.paidDate()));
        var p=new MapSqlParameterSource("id",id).addValue("request",input.requestId()).addValue("hash",fingerprint).addValue("actor",actor).addValue("method",method).addValue("reference",reference).addValue("date",java.sql.Date.valueOf(input.paidDate()));
        var rows=jdbc.queryForList("SELECT * FROM finance_operating_entries WHERE id=:id FOR UPDATE",p);if(rows.isEmpty())throw new NoSuchElementException("Operating entry not found");var entry=rows.get(0);
        if(entry.get("status").equals("SETTLED")){
            if(input.requestId().equals(entry.get("settlement_request_id"))&&fingerprint.equals(entry.get("settlement_hash"))&&actor.equals(entry.get("settled_by")))return detail(id);
            throw new ConflictException("This entry already has a recorded settlement");
        }
        validateSettlement((String)entry.get("status"),((java.sql.Date)entry.get("document_date")).toLocalDate(),input.paidDate(),today());
        jdbc.update("UPDATE finance_operating_entries SET status='SETTLED',settlement_request_id=:request,settlement_hash=:hash,settlement_method=:method,settlement_reference=:reference,settlement_date=:date,settled_by=:actor,settled_at=now() WHERE id=:id",p);
        return detail(id);
    }
}
