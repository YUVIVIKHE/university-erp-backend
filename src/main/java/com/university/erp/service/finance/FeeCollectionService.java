package com.university.erp.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.erp.exception.ConflictException;
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
public class FeeCollectionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
    public record Allocation(@NotBlank @Size(max=30) String feeId,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount){}
    public record PaymentInput(@NotNull UUID requestId,@NotBlank @Size(max=30) String admissionId,
        @NotBlank String method,@Size(max=100) String reference,@NotNull LocalDate paidDate,
        @Size(max=1000) String notes,@NotNull @AssertTrue Boolean verified,
        @NotEmpty @Size(max=100) List<@NotNull @Valid Allocation> allocations){}
    public record Payment(UUID id,String receiptNumber,String admissionId,String studentName,String program,
        BigDecimal amount,String method,String reference,LocalDate paidDate,String notes,String recordedBy,OffsetDateTime createdAt){}
    public record ReceiptLine(String feeId,String feeType,BigDecimal amount,BigDecimal paidBefore,BigDecimal balanceAfter){}
    public record Receipt(Payment payment,List<ReceiptLine> allocations){}
    public record Charge(String feeId,String feeType,BigDecimal amount,BigDecimal paid,BigDecimal balance,LocalDate dueDate){}
    static String method(String value){
        String mode=value.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("CASH","BANK_TRANSFER","UPI","CARD","CHEQUE").contains(mode))throw new IllegalArgumentException("Unsupported payment method");return mode;
    }
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    static List<Allocation> ordered(List<Allocation> allocations){
        var sorted=allocations.stream().sorted(Comparator.comparing(Allocation::feeId)).toList();
        if(sorted.stream().map(Allocation::feeId).distinct().count()!=sorted.size())throw new IllegalArgumentException("Select each charge only once");return sorted;
    }
    static BigDecimal validateBalance(BigDecimal amount,BigDecimal paid,BigDecimal requested,String status){
        if(requested.signum()<=0)throw new IllegalArgumentException("Payment amount must be positive");
        if("WAIVED".equals(status))throw new IllegalArgumentException("Payments cannot be allocated to waived charges");
        BigDecimal remaining=amount.subtract(paid);
        if(requested.compareTo(remaining)>0)throw new ConflictException("Payment exceeds the current charge balance. Refresh the account.");
        return remaining.subtract(requested);
    }
    private String fingerprint(PaymentInput input,String mode,String reference,List<Allocation> lines){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(
            Arrays.asList(input.admissionId(),mode,reference,input.paidDate(),optional(input.notes()),lines.stream().map(l->List.of(l.feeId(),l.amount().stripTrailingZeros().toPlainString())).toList()))));}
        catch(Exception e){throw new IllegalArgumentException("Payment request could not be validated");}
    }
    private Payment payment(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        return new Payment(rs.getObject("id",UUID.class),rs.getString("receipt_number"),rs.getString("admission_id"),rs.getString("student_name"),rs.getString("program"),rs.getBigDecimal("amount"),rs.getString("method"),rs.getString("reference"),rs.getDate("paid_date").toLocalDate(),rs.getString("notes"),rs.getString("recorded_by"),rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC));
    }
    @Transactional public Receipt collect(PaymentInput input,String actor){
        LocalDate today=LocalDate.now(ZoneId.of(timeZone));
        if(input.paidDate().isAfter(today))throw new IllegalArgumentException("Payment date cannot be in the future");
        String mode=method(input.method()),reference=optional(input.reference());
        if(reference!=null)reference=reference.toUpperCase(Locale.ROOT);
        if(!mode.equals("CASH")&&reference==null)throw new IllegalArgumentException("A verified transaction or cheque reference is required");
        var lines=ordered(input.allocations());BigDecimal total=lines.stream().map(Allocation::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo(new BigDecimal("999999999999.99"))>0)throw new IllegalArgumentException("Payment total exceeds the supported amount");
        String hash=fingerprint(input,mode,reference,lines);UUID id=UUID.randomUUID();
        var p=new MapSqlParameterSource("id",id).addValue("request",input.requestId()).addValue("hash",hash).addValue("actor",actor)
            .addValue("admission",input.admissionId()).addValue("amount",total).addValue("method",mode).addValue("reference",reference)
            .addValue("paidDate",java.sql.Date.valueOf(input.paidDate())).addValue("notes",optional(input.notes()));
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:request AS text),0))",p);
        var existing=jdbc.queryForList("SELECT id,request_hash,recorded_by FROM finance_fee_payments WHERE request_id=:request",p);
        if(!existing.isEmpty()){
            var previous=existing.get(0);
            if(!hash.equals(previous.get("request_hash"))||!actor.equals(previous.get("recorded_by")))throw new ConflictException("This request ID was already used for another payment");
            return receipt((UUID)previous.get("id"));
        }
        var admission=jdbc.queryForList("SELECT first_name||' '||last_name AS name,program FROM admissions WHERE admission_id=:admission",p);
        if(admission.isEmpty())throw new NoSuchElementException("Admission not found");
        p.addValue("name",admission.get(0).get("name")).addValue("program",admission.get(0).get("program"));
        int inserted=jdbc.update("""
            INSERT INTO finance_fee_payments(id,request_id,request_hash,admission_id,student_name,program,amount,method,reference,paid_date,notes,recorded_by)
            VALUES(:id,:request,:hash,:admission,:name,:program,:amount,:method,:reference,:paidDate,:notes,:actor)
            ON CONFLICT(request_id) DO NOTHING
            """,p);
        if(inserted==0){
            var previous=jdbc.queryForMap("SELECT id,request_hash,recorded_by FROM finance_fee_payments WHERE request_id=:request",p);
            if(!hash.equals(previous.get("request_hash"))||!actor.equals(previous.get("recorded_by")))throw new ConflictException("This request ID was already used for another payment");
            return receipt((UUID)previous.get("id"));
        }
        // Lock in fee-ID order so overlapping multi-charge payments cannot deadlock.
        p.addValue("fees",lines.stream().map(Allocation::feeId).toList());
        var fees=jdbc.queryForList("SELECT * FROM fees WHERE fee_id IN (:fees) ORDER BY fee_id FOR UPDATE",p);
        if(fees.size()!=lines.size())throw new NoSuchElementException("A selected fee charge no longer exists");
        Map<String,Allocation> allocationByFee=new HashMap<>();lines.forEach(line->allocationByFee.put(line.feeId(),line));
        for(var fee:fees){
            // Database collation can differ from Java string ordering; match by ID.
            var line=allocationByFee.get((String)fee.get("fee_id"));
            if(!input.admissionId().equals(fee.get("admission_id")))throw new IllegalArgumentException("All selected charges must belong to the same admission");
            BigDecimal paid=(BigDecimal)fee.get("amount_paid"),amount=((BigDecimal)fee.get("amount")).subtract((BigDecimal)fee.get("concession_amount"));
            BigDecimal remaining=validateBalance(amount,paid,line.amount(),(String)fee.get("status"));
            BigDecimal newPaid=paid.add(line.amount());LocalDate due=((java.sql.Date)fee.get("due_date")).toLocalDate();
            String status=remaining.signum()==0?"PAID":due.isBefore(today)?"OVERDUE":"PARTIAL";
            p.addValue("fee",line.feeId()).addValue("type",fee.get("fee_type")).addValue("allocation",line.amount())
                .addValue("before",paid).addValue("balance",remaining).addValue("newPaid",newPaid).addValue("status",status);
            jdbc.update("INSERT INTO finance_fee_payment_allocations(payment_id,fee_id,fee_type,amount,paid_before,balance_after) VALUES(:id,:fee,:type,:allocation,:before,:balance)",p);
            jdbc.update("UPDATE fees SET amount_paid=:newPaid,status=:status,paid_date=GREATEST(COALESCE(paid_date,:paidDate),:paidDate),updated_at=now() WHERE fee_id=:fee",p);
        }
        p.addValue("year",today.getYear());
        jdbc.update("UPDATE finance_fee_payments SET receipt_number='RCP-'||:year||'-'||lpad(sequence_number::text,GREATEST(8,length(sequence_number::text)),'0') WHERE id=:id",p);
        return receipt(id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Receipt receipt(UUID id){
        var p=Map.of("id",id);var payments=jdbc.query("SELECT * FROM finance_fee_payments WHERE id=:id",p,this::payment);
        if(payments.isEmpty())throw new NoSuchElementException("Receipt not found");
        var allocations=jdbc.query("SELECT fee_id,fee_type,amount,paid_before,balance_after FROM finance_fee_payment_allocations WHERE payment_id=:id ORDER BY fee_id",p,
            (rs,n)->new ReceiptLine(rs.getString(1),rs.getString(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(5)));
        return new Receipt(payments.get(0),allocations);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page<Charge> charges(String admissionId,int page){
        var p=new MapSqlParameterSource("admission",admissionId).addValue("offset",page*20L);
        String where=" FROM fees WHERE admission_id=:admission AND status<>'WAIVED' AND amount-concession_amount>amount_paid";
        long total=jdbc.queryForObject("SELECT count(*)"+where,p,Long.class);
        var rows=jdbc.query("SELECT fee_id,fee_type,amount,amount_paid,amount-concession_amount-amount_paid,due_date"+where+" ORDER BY due_date,id LIMIT 20 OFFSET :offset",p,
            (rs,n)->new Charge(rs.getString(1),rs.getString(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getDate(6).toLocalDate()));
        return new PageImpl<>(rows,PageRequest.of(page,20),total);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Page<Payment> history(String search,String admissionId,LocalDate from,LocalDate until,int page){
        if(from!=null&&until!=null&&until.isBefore(from))throw new IllegalArgumentException("End date must be on or after start date");
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("admission",admissionId).addValue("offset",page*20L);
        String where=" FROM finance_fee_payments WHERE (receipt_number ILIKE :search OR student_name ILIKE :search OR admission_id ILIKE :search OR COALESCE(reference,'') ILIKE :search) AND (:admission='' OR admission_id=:admission)";
        if(from!=null){where+=" AND paid_date>=:from";p.addValue("from",java.sql.Date.valueOf(from));}
        if(until!=null){where+=" AND paid_date<=:until";p.addValue("until",java.sql.Date.valueOf(until));}
        long total=jdbc.queryForObject("SELECT count(*)"+where,p,Long.class);
        return new PageImpl<>(jdbc.query("SELECT *"+where+" ORDER BY sequence_number DESC LIMIT 20 OFFSET :offset",p,this::payment),PageRequest.of(page,20),total);
    }
}
