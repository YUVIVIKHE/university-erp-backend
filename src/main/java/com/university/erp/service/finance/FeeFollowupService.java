package com.university.erp.service.finance;

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
public class FeeFollowupService {
    private final NamedParameterJdbcTemplate jdbc;
    @Value("${app.finance.time-zone:Asia/Kolkata}") private String timeZone="Asia/Kolkata";
    public record Account(String admissionId,String studentName,String program,int semester,int batchYear,
        BigDecimal outstanding,BigDecimal overdue,BigDecimal aging30,BigDecimal aging60,BigDecimal aging90,BigDecimal agingOlder,
        LocalDate earliestDue,String latestOutcome,LocalDate promisedDate,BigDecimal promisedAmount,LocalDate nextContactDate){}
    public record Totals(long accounts,BigDecimal outstanding,BigDecimal overdue){}
    public record Worklist(LocalDate asOf,Totals totals,Page<Account> accounts){}
    public record Input(@NotNull UUID requestId,@NotBlank String channel,@NotBlank String outcome,
        @NotBlank @Size(max=2000) String note,LocalDate promisedDate,
        @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal promisedAmount,LocalDate nextContactDate){}
    public record Followup(long id,String admissionId,String channel,String outcome,String note,LocalDate promisedDate,
        BigDecimal promisedAmount,LocalDate nextContactDate,String recordedBy,OffsetDateTime createdAt){}
    public record ReminderDraft(String recipient,String subject,String body,LocalDate asOf){}
    private LocalDate today(){return LocalDate.now(ZoneId.of(timeZone));}
    private static LocalDate date(java.sql.ResultSet rs,String field)throws java.sql.SQLException{var value=rs.getDate(field);return value==null?null:value.toLocalDate();}
    static void validate(Input input,LocalDate today){
        if(!Set.of("PHONE","EMAIL","IN_PERSON","OTHER").contains(input.channel()))throw new IllegalArgumentException("Invalid contact channel");
        if(!Set.of("OPEN","CONTACTED","PROMISED","DISPUTED").contains(input.outcome()))throw new IllegalArgumentException("Invalid follow-up status");
        if(input.outcome().equals("PROMISED")){
            if(input.promisedDate()==null||input.promisedAmount()==null)throw new IllegalArgumentException("A promise requires its date and amount");
            if(input.promisedAmount().signum()<=0)throw new IllegalArgumentException("Promised amount must be positive");
            if(input.promisedDate().isBefore(today))throw new IllegalArgumentException("Promise date cannot be in the past");
        } else if(input.promisedDate()!=null||input.promisedAmount()!=null)throw new IllegalArgumentException("Promise details are only allowed for Promised status");
        if(input.nextContactDate()!=null&&input.nextContactDate().isBefore(today))throw new IllegalArgumentException("Next contact date cannot be in the past");
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Worklist list(String search,String program,int semester,int batch,String scope,String aging,String outcome,int page){
        if(!Set.of("OUTSTANDING","OVERDUE").contains(scope))throw new IllegalArgumentException("Invalid balance scope");
        if(!Set.of("ALL","1_30","31_60","61_90","90_PLUS").contains(aging))throw new IllegalArgumentException("Invalid aging bucket");
        if(!Set.of("","OPEN","CONTACTED","PROMISED","DISPUTED").contains(outcome))throw new IllegalArgumentException("Invalid follow-up status");
        LocalDate today=today();
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("program",program.trim()).addValue("semester",semester).addValue("batch",batch)
            .addValue("today",java.sql.Date.valueOf(today)).addValue("outcome",outcome).addValue("offset",page*20L);
        String eligibility="true";
        if(scope.equals("OVERDUE"))eligibility+=" AND f.due_date<:today";
        eligibility+=switch(aging){
            case "1_30"->" AND f.due_date>=:day30 AND f.due_date<:today";
            case "31_60"->" AND f.due_date>=:day60 AND f.due_date<:day30";
            case "61_90"->" AND f.due_date>=:day90 AND f.due_date<:day60";
            case "90_PLUS"->" AND f.due_date<:day90";
            default->"";
        };
        p.addValue("day30",java.sql.Date.valueOf(today.minusDays(30))).addValue("day60",java.sql.Date.valueOf(today.minusDays(60))).addValue("day90",java.sql.Date.valueOf(today.minusDays(90)));
        String filter="""
            WHERE (a.first_name||' '||a.last_name ILIKE :search OR a.admission_id ILIKE :search)
              AND (:program='' OR lower(a.program)=lower(:program)) AND (:semester=0 OR a.semester=:semester)
              AND (:batch=0 OR a.batch_year=:batch)
              AND f.status<>'WAIVED' AND f.amount-f.concession_amount>f.amount_paid
            """;
        String balances="""
            WITH balances AS (
              SELECT a.id,a.admission_id,a.first_name||' '||a.last_name AS student_name,a.program,a.semester,a.batch_year,
                sum(f.amount-f.concession_amount-f.amount_paid) AS outstanding,
                coalesce(sum(f.amount-f.concession_amount-f.amount_paid) FILTER(WHERE f.due_date<:today),0) AS overdue,
                coalesce(sum(f.amount-f.concession_amount-f.amount_paid) FILTER(WHERE f.due_date>=:day30 AND f.due_date<:today),0) AS aging30,
                coalesce(sum(f.amount-f.concession_amount-f.amount_paid) FILTER(WHERE f.due_date>=:day60 AND f.due_date<:day30),0) AS aging60,
                coalesce(sum(f.amount-f.concession_amount-f.amount_paid) FILTER(WHERE f.due_date>=:day90 AND f.due_date<:day60),0) AS aging90,
                coalesce(sum(f.amount-f.concession_amount-f.amount_paid) FILTER(WHERE f.due_date<:day90),0) AS aging_older,min(f.due_date) AS earliest_due
              FROM admissions a JOIN fees f ON f.admission_id=a.admission_id
            """+filter+" GROUP BY a.id HAVING bool_or("+eligibility+")) ";
        String statusFilter=" WHERE (:outcome='' OR COALESCE((SELECT outcome FROM finance_fee_followups WHERE admission_id=b.admission_id ORDER BY id DESC LIMIT 1),'OPEN')=:outcome) ";
        var totals=jdbc.queryForObject(balances+"SELECT count(*),coalesce(sum(outstanding),0),coalesce(sum(overdue),0) FROM balances b"+statusFilter,p,
            (rs,n)->new Totals(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3)));
        String query=balances+" SELECT b.*,contact.outcome,contact.promised_date,contact.promised_amount,contact.next_contact_date FROM (SELECT * FROM balances b "+statusFilter+" ORDER BY earliest_due,id LIMIT 20 OFFSET :offset) b "+"""
            LEFT JOIN LATERAL (
              SELECT outcome,promised_date,promised_amount,next_contact_date FROM finance_fee_followups
              WHERE admission_id=b.admission_id ORDER BY id DESC LIMIT 1
            ) contact ON true ORDER BY b.earliest_due,b.id
            """;
        var rows=jdbc.query(query,p,(rs,n)->new Account(rs.getString("admission_id"),rs.getString("student_name"),rs.getString("program"),rs.getInt("semester"),rs.getInt("batch_year"),
            rs.getBigDecimal("outstanding"),rs.getBigDecimal("overdue"),rs.getBigDecimal("aging30"),rs.getBigDecimal("aging60"),rs.getBigDecimal("aging90"),rs.getBigDecimal("aging_older"),
            date(rs,"earliest_due"),Optional.ofNullable(rs.getString("outcome")).orElse("OPEN"),date(rs,"promised_date"),rs.getBigDecimal("promised_amount"),date(rs,"next_contact_date")));
        return new Worklist(today,totals,new PageImpl<>(rows,PageRequest.of(page,20),totals.accounts()));
    }
    private Followup followup(java.sql.ResultSet rs,int row)throws java.sql.SQLException{
        return new Followup(rs.getLong("id"),rs.getString("admission_id"),rs.getString("channel"),rs.getString("outcome"),rs.getString("note"),date(rs,"promised_date"),rs.getBigDecimal("promised_amount"),date(rs,"next_contact_date"),rs.getString("recorded_by"),rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Followup> history(String admission,int page){
        var p=new MapSqlParameterSource("admission",admission).addValue("offset",page*20L);
        long count=jdbc.queryForObject("SELECT count(*) FROM finance_fee_followups WHERE admission_id=:admission",p,Long.class);
        return new PageImpl<>(jdbc.query("SELECT * FROM finance_fee_followups WHERE admission_id=:admission ORDER BY id DESC LIMIT 20 OFFSET :offset",p,this::followup),PageRequest.of(page,20),count);
    }
    @Transactional public Followup record(String admission,Input input,String actor){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM admissions WHERE admission_id=:id)",Map.of("id",admission),Boolean.class)))throw new NoSuchElementException("Admission not found");
        var p=new MapSqlParameterSource("admission",admission).addValue("request",input.requestId()).addValue("channel",input.channel()).addValue("outcome",input.outcome())
            .addValue("note",input.note().trim()).addValue("promisedDate",input.promisedDate()==null?null:java.sql.Date.valueOf(input.promisedDate()),java.sql.Types.DATE)
            .addValue("promisedAmount",input.promisedAmount(),java.sql.Types.NUMERIC).addValue("nextDate",input.nextContactDate()==null?null:java.sql.Date.valueOf(input.nextContactDate()),java.sql.Types.DATE).addValue("actor",actor);
        String matching="""
            SELECT * FROM finance_fee_followups WHERE request_id=:request AND admission_id=:admission AND recorded_by=:actor
              AND channel=:channel AND outcome=:outcome AND note=:note AND promised_date IS NOT DISTINCT FROM :promisedDate
              AND promised_amount IS NOT DISTINCT FROM :promisedAmount AND next_contact_date IS NOT DISTINCT FROM :nextDate
            """;
        var existing=jdbc.query(matching,p,this::followup);
        if(!existing.isEmpty())return existing.get(0);
        validate(input,today());
        var created=jdbc.query("""
            INSERT INTO finance_fee_followups(request_id,admission_id,channel,outcome,note,promised_date,promised_amount,next_contact_date,recorded_by)
            VALUES(:request,:admission,:channel,:outcome,:note,:promisedDate,:promisedAmount,:nextDate,:actor)
            ON CONFLICT(request_id) DO NOTHING RETURNING *
            """,p,this::followup);
        if(!created.isEmpty())return created.get(0);
        var previous=jdbc.query(matching,p,this::followup);
        if(previous.isEmpty())throw new ConflictException("This request ID was used for another follow-up");return previous.get(0);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public ReminderDraft draft(String admission){
        LocalDate asOf=today();
        var rows=jdbc.queryForList("""
            SELECT a.first_name||' '||a.last_name AS name,a.email,
              coalesce(sum(f.amount-f.concession_amount-f.amount_paid),0) AS outstanding,
              coalesce(sum(f.amount-f.concession_amount-f.amount_paid) FILTER(WHERE f.due_date<:today),0) AS overdue
            FROM admissions a LEFT JOIN fees f ON f.admission_id=a.admission_id AND f.status<>'WAIVED' AND f.amount-f.concession_amount>f.amount_paid
            WHERE a.admission_id=:admission GROUP BY a.id
            """,Map.of("admission",admission,"today",java.sql.Date.valueOf(asOf)));
        if(rows.isEmpty())throw new NoSuchElementException("Admission not found");var row=rows.get(0);
        if(((BigDecimal)row.get("outstanding")).signum()==0)throw new ConflictException("There is no outstanding balance to remind about");
        var currency=java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        String body="Hello "+row.get("name")+",\n\nOur fee records as of "+asOf+" show:\nAdmission ID: "+admission+"\nOutstanding fees: "+currency.format(row.get("outstanding"))+"\nOverdue portion: "+currency.format(row.get("overdue"))+"\n\nPlease contact the Accounts office about your outstanding fees. If you have already paid or have an approved concession, please share the relevant reference so we can verify our records.\n\nAccounts & Finance";
        return new ReminderDraft((String)row.get("email"),"Fee balance reminder — "+admission,body,asOf);
    }
}
