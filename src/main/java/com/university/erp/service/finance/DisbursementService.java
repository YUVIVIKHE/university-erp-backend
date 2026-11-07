package com.university.erp.service.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.erp.exception.ConflictException;
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
public class DisbursementService {
    private final NamedParameterJdbcTemplate jdbc;
    private final OperatingFinanceService operations;
    private final ObjectMapper mapper;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
    public record VendorInput(@NotNull UUID requestId,@NotBlank @Size(max=150) String name,@Email @Size(max=255) String email){}
    public record Active(@NotNull Boolean active){}
    public record Invoice(@NotNull UUID requestId,@NotNull UUID vendorId,@NotBlank @Size(max=150) String reference,@NotNull LocalDate date,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank @Size(max=2000) String notes){}
    public record Payroll(@NotNull UUID requestId,@NotNull @Positive Long userId,@NotNull LocalDate month,@NotBlank @Size(max=150) String reference,@NotNull LocalDate date,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank @Size(max=2000) String notes){}
    static void month(LocalDate month,LocalDate today){if(month.getDayOfMonth()!=1||YearMonth.from(month).isAfter(YearMonth.from(today)))throw new IllegalArgumentException("Choose a current or past payroll month");}
    private String hash(Object input){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(input)));}catch(Exception e){throw new IllegalArgumentException("Invalid vendor request");}}
    private Map<String,Object> vendorRow(Map<String,Object> row){var r=new LinkedHashMap<>(row);r.remove("request_hash");return r;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Map<String,Object>> vendors(String search,String status,int page){
        if(!Set.of("","ACTIVE","INACTIVE").contains(status))throw new IllegalArgumentException("Invalid vendor status");
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("offset",page*20L);String where=" WHERE name ILIKE :search"+(status.isEmpty()?"":status.equals("ACTIVE")?" AND active=true":" AND active=false");
        long count=jdbc.queryForObject("SELECT count(*) FROM finance_vendors"+where,p,Long.class);
        return new PageImpl<>(jdbc.queryForList("SELECT * FROM finance_vendors"+where+" ORDER BY name,id LIMIT 20 OFFSET :offset",p).stream().map(this::vendorRow).toList(),PageRequest.of(page,20),count);
    }
    @Transactional public Map<String,Object> createVendor(VendorInput input,String actor){
        String email=input.email()==null||input.email().isBlank()?null:input.email().trim();String hash=hash(Arrays.asList(input.name().trim(),email));
        var p=new MapSqlParameterSource("request",input.requestId()).addValue("hash",hash).addValue("actor",actor).addValue("id",UUID.randomUUID()).addValue("name",input.name().trim()).addValue("email",email,java.sql.Types.VARCHAR);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:request AS text),0))",p);
        var old=jdbc.queryForList("SELECT * FROM finance_vendors WHERE request_id=:request",p);
        if(!old.isEmpty()){if(!hash.equals(old.get(0).get("request_hash"))||!actor.equals(old.get(0).get("created_by")))throw new ConflictException("Vendor request ID already used");return vendorRow(old.get(0));}
        var created=jdbc.queryForList("INSERT INTO finance_vendors(id,request_id,request_hash,name,email,created_by) VALUES(:id,:request,:hash,:name,:email,:actor) ON CONFLICT DO NOTHING RETURNING *",p);
        if(created.isEmpty())throw new ConflictException("Vendor name is already registered");return vendorRow(created.get(0));
    }
    @Transactional public Map<String,Object> active(UUID id,boolean active){
        var result=jdbc.queryForList("UPDATE finance_vendors SET active=:active WHERE id=:id RETURNING *",Map.of("id",id,"active",active));if(result.isEmpty())throw new NoSuchElementException("Vendor not found");return vendorRow(result.get(0));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Map<String,Object>> staff(String search,int page){
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("offset",page*20L);String where=" WHERE is_active=true AND invitation_pending=false AND role IN('ADMIN','ACCOUNTANT','FACULTY') AND (full_name ILIKE :search OR email ILIKE :search) ";
        long count=jdbc.queryForObject("SELECT count(*) FROM users"+where,p,Long.class);return new PageImpl<>(jdbc.queryForList("SELECT id,full_name,email,role FROM users"+where+" ORDER BY full_name,id LIMIT 20 OFFSET :offset",p),PageRequest.of(page,20),count);
    }
    private void lock(UUID request){jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:id AS text),0))",Map.of("id",request));}
    @Transactional public Map<String,Object> invoice(Invoice input,String actor){
        lock(input.requestId());var old=jdbc.queryForList("SELECT vendor_id FROM finance_operating_entries WHERE request_id=:id",Map.of("id",input.requestId()));
        if(!old.isEmpty()&&!input.vendorId().equals(old.get(0).get("vendor_id")))throw new ConflictException("Request ID already used for another payee");
        var vendor=jdbc.queryForList("SELECT * FROM finance_vendors WHERE id=:id FOR SHARE",Map.of("id",input.vendorId()));
        if(vendor.isEmpty())throw new NoSuchElementException("Vendor not found");if(old.isEmpty()&&!Boolean.TRUE.equals(vendor.get(0).get("active")))throw new ConflictException("Inactive vendors cannot receive new invoice requests");
        var entry=operations.createDisbursement(new OperatingFinanceService.Input(input.requestId(),"EXPENSE","VENDOR_PAYMENT",(String)vendor.get(0).get("name"),input.reference(),input.date(),input.amount(),input.notes()),actor);
        jdbc.update("UPDATE finance_operating_entries SET vendor_id=:vendor WHERE id=:id",Map.of("vendor",input.vendorId(),"id",entry.get("id")));return detail((UUID)entry.get("id"),"VENDOR");
    }
    @Transactional public Map<String,Object> payroll(Payroll input,String actor){
        month(input.month(),LocalDate.now(ZoneId.of(timeZone)));lock(input.requestId());
        var old=jdbc.queryForList("SELECT staff_user_id,payroll_month,counterparty FROM finance_operating_entries WHERE request_id=:id",Map.of("id",input.requestId()));
        if(!old.isEmpty()&&(!(old.get(0).get("staff_user_id") instanceof Number n)||n.longValue()!=input.userId()||!java.sql.Date.valueOf(input.month()).equals(old.get(0).get("payroll_month"))))throw new ConflictException("Request ID already used for another payroll period/payee");
        var users=jdbc.queryForList("SELECT full_name,is_active,invitation_pending,role FROM users WHERE id=:id FOR SHARE",Map.of("id",input.userId()));
        if(users.isEmpty())throw new NoSuchElementException("Staff user not found");var user=users.get(0);
        if(old.isEmpty()&&(!Boolean.TRUE.equals(user.get("is_active"))||Boolean.TRUE.equals(user.get("invitation_pending"))||!Set.of("ADMIN","ACCOUNTANT","FACULTY").contains(user.get("role"))))throw new IllegalArgumentException("Choose an active staff user");
        if(old.isEmpty()&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM finance_payroll_profiles WHERE user_id=:id AND active=false)",Map.of("id",input.userId()),Boolean.class)))throw new ConflictException("Payroll profile is paused");
        String reference=input.month().toString().substring(0,7)+" / U"+input.userId()+" / "+input.reference().trim();
        if(reference.length()>150)throw new IllegalArgumentException("Payroll reference must be at most 140 characters");
        // Staff identity is part of the immutable document reference, even when names coincide.
        String party=old.isEmpty()?user.get("full_name").toString():old.get(0).get("counterparty").toString();if(party.length()>150)throw new IllegalArgumentException("Staff name exceeds supported payee length");
        var entry=operations.createDisbursement(new OperatingFinanceService.Input(input.requestId(),"EXPENSE","PAYROLL",party,reference,input.date(),input.amount(),input.notes()),actor);
        jdbc.update("UPDATE finance_operating_entries SET staff_user_id=:staff,payroll_month=:month WHERE id=:id",Map.of("staff",input.userId(),"month",java.sql.Date.valueOf(input.month()),"id",entry.get("id")));return detail((UUID)entry.get("id"),"PAYROLL");
    }
    @Transactional(readOnly=true) public Map<String,Object> detail(UUID id,String type){
        var row=operations.detail(id);if(type.equals("VENDOR")?row.get("vendor_id")==null:row.get("staff_user_id")==null)throw new NoSuchElementException("Payment request not found");return row;
    }
    @Transactional public Map<String,Object> review(UUID id,String type,OperatingFinanceService.Review input,String actor){detail(id,type);return operations.review(id,input,actor);}
    @Transactional public Map<String,Object> settle(UUID id,String type,OperatingFinanceService.Settlement input,String actor){detail(id,type);return operations.settle(id,input,actor);}
}
