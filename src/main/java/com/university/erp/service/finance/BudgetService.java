package com.university.erp.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.erp.exception.ConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class BudgetService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public record Line(@NotBlank String category,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount){}
    public record Input(@NotNull UUID requestId,@NotBlank @Size(max=150) String name,@NotNull LocalDate periodStart,@NotNull LocalDate periodEnd,
        @NotBlank @Size(max=1000) String notes,@NotEmpty @Size(max=5) List<@NotNull @Valid Line> lines){}
    static BigDecimal validate(Input input){
        if(input.periodEnd().isBefore(input.periodStart()))throw new IllegalArgumentException("Budget end must be on or after its start");
        var categories=new HashSet<String>();BigDecimal total=BigDecimal.ZERO;
        for(var line:input.lines()){
            if(!OperatingFinanceService.CATEGORIES.get("EXPENSE").contains(line.category()))throw new IllegalArgumentException("Select an existing operating expense category");
            if(!categories.add(line.category()))throw new IllegalArgumentException("Allocate each category only once");
            total=total.add(line.amount());
        }
        return total;
    }
    private Map<String,Object> publicRow(Map<String,Object> row){var result=new LinkedHashMap<>(row);result.remove("request_hash");return result;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Map<String,Object>> list(String search,String status,int page){
        if(!Set.of("","PENDING","APPROVED","REJECTED","CANCELLED").contains(status))throw new IllegalArgumentException("Invalid budget status");
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("status",status).addValue("offset",page*20L);
        String where=" WHERE name ILIKE :search AND (:status='' OR status=:status) ";
        long total=jdbc.queryForObject("SELECT count(*) FROM finance_budgets"+where,p,Long.class);
        var rows=jdbc.queryForList("SELECT * FROM finance_budgets"+where+" ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET :offset",p).stream().map(this::publicRow).toList();
        return new PageImpl<>(rows,PageRequest.of(page,20),total);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(UUID id){
        var p=new MapSqlParameterSource("id",id);var headers=jdbc.queryForList("SELECT * FROM finance_budgets WHERE id=:id",p);
        if(headers.isEmpty())throw new NoSuchElementException("Budget not found");var result=publicRow(headers.get(0));
        p.addValue("start",result.get("period_start")).addValue("end",result.get("period_end"));
        var lines=jdbc.queryForList("""
            SELECT l.category,l.amount AS allocated,coalesce(a.actual,0) AS actual,coalesce(c.committed,0) AS committed,
              l.amount-coalesce(a.actual,0)-coalesce(c.committed,0) AS remaining
            FROM finance_budget_lines l
            LEFT JOIN LATERAL(SELECT sum(o.amount) AS actual FROM finance_operating_entries o
              WHERE o.direction='EXPENSE' AND o.status='SETTLED' AND o.category=l.category AND o.settlement_date BETWEEN :start AND :end) a ON true
            LEFT JOIN LATERAL(SELECT sum(o.amount) AS committed FROM finance_operating_entries o
              WHERE o.direction='EXPENSE' AND o.status='APPROVED' AND o.category=l.category AND o.document_date BETWEEN :start AND :end) c ON true
            WHERE l.budget_id=:id ORDER BY l.category
            """,p);
        BigDecimal actual=BigDecimal.ZERO,committed=BigDecimal.ZERO;
        for(var line:lines){actual=actual.add((BigDecimal)line.get("actual"));committed=committed.add((BigDecimal)line.get("committed"));}
        result.put("lines",lines);result.put("actual",actual);result.put("committed",committed);result.put("remaining",((BigDecimal)result.get("amount")).subtract(actual).subtract(committed));return result;
    }
    @Transactional public Map<String,Object> create(Input input,String actor){
        BigDecimal total=validate(input);String fingerprint;
        try{fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(List.of(input.name().trim(),input.periodStart(),input.periodEnd(),input.notes().trim(),input.lines().stream().sorted(Comparator.comparing(Line::category)).map(l->List.of(l.category(),l.amount().stripTrailingZeros().toPlainString())).toList()))));}
        catch(Exception e){throw new IllegalArgumentException("Invalid budget request");}
        var p=new MapSqlParameterSource("id",UUID.randomUUID()).addValue("request",input.requestId()).addValue("hash",fingerprint).addValue("actor",actor)
            .addValue("name",input.name().trim()).addValue("start",java.sql.Date.valueOf(input.periodStart())).addValue("end",java.sql.Date.valueOf(input.periodEnd())).addValue("amount",total).addValue("notes",input.notes().trim());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:request AS text),0))",p);
        var old=jdbc.queryForList("SELECT * FROM finance_budgets WHERE request_id=:request",p);
        if(!old.isEmpty()){
            if(!fingerprint.equals(old.get(0).get("request_hash"))||!actor.equals(old.get(0).get("created_by")))throw new ConflictException("Request ID already used for another budget");return detail((UUID)old.get(0).get("id"));
        }
        int inserted=jdbc.update("INSERT INTO finance_budgets(id,request_id,request_hash,name,period_start,period_end,amount,notes,created_by) VALUES(:id,:request,:hash,:name,:start,:end,:amount,:notes,:actor) ON CONFLICT DO NOTHING",p);
        if(inserted==0)throw new ConflictException("An active budget already has this name and period");
        for(var line:input.lines()){p.addValue("category",line.category()).addValue("lineAmount",line.amount());jdbc.update("INSERT INTO finance_budget_lines(budget_id,category,amount) VALUES(:id,:category,:lineAmount)",p);}
        return detail((UUID)p.getValue("id"));
    }
    @Transactional public Map<String,Object> review(UUID id,OperatingFinanceService.Review input,String actor){
        var p=new MapSqlParameterSource("id",id);var rows=jdbc.queryForList("SELECT * FROM finance_budgets WHERE id=:id FOR UPDATE",p);
        if(rows.isEmpty())throw new NoSuchElementException("Budget not found");var b=rows.get(0);
        if(input.status().equals(b.get("status"))&&actor.equals(b.get("reviewed_by"))&&input.note().trim().equals(b.get("review_note")))return detail(id);
        OperatingFinanceService.validateReview((String)b.get("status"),(String)b.get("created_by"),actor,input);
        if(input.status().equals("APPROVED")){
            // Serialize approvals, including different budgets for the same category/date range.
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('finance-budget-approval',0))",Map.of());
            p.addValue("start",b.get("period_start")).addValue("end",b.get("period_end"));
            long overlaps=jdbc.queryForObject("""
                SELECT count(*) FROM finance_budgets other JOIN finance_budget_lines l ON l.budget_id=other.id
                WHERE other.status='APPROVED' AND other.period_start<=:end AND other.period_end>=:start
                  AND l.category IN(SELECT category FROM finance_budget_lines WHERE budget_id=:id)
                """,p,Long.class);
            if(overlaps>0)throw new ConflictException("An approved budget already covers a selected category in this period");
        }
        p.addValue("status",input.status()).addValue("actor",actor).addValue("note",input.note().trim());
        jdbc.update("UPDATE finance_budgets SET status=:status,reviewed_by=:actor,reviewed_at=now(),review_note=:note WHERE id=:id",p);return detail(id);
    }
}
