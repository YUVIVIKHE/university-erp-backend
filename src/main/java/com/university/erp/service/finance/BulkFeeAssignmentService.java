package com.university.erp.service.finance;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.university.erp.exception.ConflictException;
import java.time.OffsetDateTime;
import java.util.*;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class BulkFeeAssignmentService {
    private final NamedParameterJdbcTemplate jdbc;
    public record Selection(@NotBlank @Size(max=100) String program,@Min(2000) @Max(2200) Integer batchYear,
        @Size(max=100) String search,@Size(max=200) List<@NotBlank @Size(max=30) String> admissionIds) {}
    public record Target(String admissionId,String studentName,String state,String reason) {}
    public record Run(UUID id,long structureId,String structureName,String program,Integer batchYear,String status,
        OffsetDateTime expiresAt,long matched,long ready,long assigned,long skipped,long ineligible,
        BigDecimal perStudent,BigDecimal previewAmount,List<Target> sample) {}
    private MapSqlParameterSource params(UUID id,String actor){return new MapSqlParameterSource("id",id).addValue("actor",actor);}
    private Map<String,Object> structure(long id,boolean lock){
        var rows=jdbc.queryForList("SELECT * FROM finance_fee_structures WHERE id=:id"+(lock?" FOR SHARE":""),Map.of("id",id));
        if(rows.isEmpty())throw new NoSuchElementException("Fee structure not found");
        var s=rows.get(0);if(!"ACTIVE".equals(s.get("status")))throw new IllegalArgumentException("Publish an active structure before assigning fees");return s;
    }
    public List<String> programs(String search){return jdbc.queryForList("SELECT DISTINCT program FROM admissions WHERE status='APPROVED' AND lower(program) LIKE lower(:search) ORDER BY program LIMIT 50",Map.of("search","%"+search.trim()+"%"),String.class);}
    @Transactional public Run preview(long structureId,Selection selection,String actor){
        var s=structure(structureId,true);Integer batch=selection.batchYear();
        if(s.get("batch_year")!=null){int required=((Number)s.get("batch_year")).intValue();if(batch!=null&&batch!=required)throw new IllegalArgumentException("Batch must match the structure eligibility");batch=required;}
        String program=selection.program().trim();
        Boolean exists=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM admissions WHERE program=:program AND status='APPROVED')",Map.of("program",program),Boolean.class);
        if(!Boolean.TRUE.equals(exists))throw new IllegalArgumentException("Select an existing approved-admission program");
        UUID id=UUID.randomUUID();var p=params(id,actor).addValue("structure",structureId).addValue("program",program).addValue("batch",batch)
            .addValue("semester",s.get("semester")).addValue("search","%"+(selection.search()==null?"":selection.search().trim())+"%");
        boolean all=selection.admissionIds()==null||selection.admissionIds().isEmpty();
        p.addValue("all",all).addValue("selected",all?List.of("__none__"):selection.admissionIds());
        jdbc.update("INSERT INTO finance_fee_assignment_runs(id,structure_id,created_by,program,batch_year) VALUES(:id,:structure,:actor,:program,:batch)",p);
        jdbc.update("""
            INSERT INTO finance_fee_assignment_targets(run_id,admission_id,state,reason)
            SELECT :id,a.admission_id,
              CASE WHEN EXISTS(SELECT 1 FROM fees f WHERE f.admission_id=a.admission_id AND f.finance_structure_id=:structure) THEN 'SKIPPED' ELSE 'READY' END,
              CASE WHEN EXISTS(SELECT 1 FROM fees f WHERE f.admission_id=a.admission_id AND f.finance_structure_id=:structure) THEN 'Template already assigned' ELSE NULL END
            FROM admissions a WHERE a.status='APPROVED' AND a.program=:program AND a.semester=:semester
              AND (CAST(:batch AS integer) IS NULL OR a.batch_year=:batch)
              AND (lower(a.first_name||' '||a.last_name) LIKE lower(:search) OR lower(a.admission_id) LIKE lower(:search))
              AND (:all OR a.admission_id IN (:selected))
            """,p);
        return get(id,actor);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public Run get(UUID id,String actor){
        var p=params(id,actor);
        var rows=jdbc.queryForList("""
            SELECT r.*,s.name AS structure_name,
            (SELECT coalesce(sum(amount),0) FROM finance_fee_structure_lines WHERE structure_id=s.id) AS per_student
            FROM finance_fee_assignment_runs r JOIN finance_fee_structures s ON s.id=r.structure_id
            WHERE r.id=:id AND r.created_by=:actor
            """,p);
        if(rows.isEmpty())throw new NoSuchElementException("Assignment run not found");
        var r=rows.get(0);
        var counts=jdbc.queryForMap("""
            SELECT count(*) AS matched,count(*) FILTER(WHERE state='READY') AS ready,
              count(*) FILTER(WHERE state='ASSIGNED') AS assigned,count(*) FILTER(WHERE state='SKIPPED') AS skipped,
              count(*) FILTER(WHERE state='INELIGIBLE') AS ineligible FROM finance_fee_assignment_targets WHERE run_id=:id
            """,p);
        var sample=jdbc.query("""
            SELECT t.admission_id,a.first_name||' '||a.last_name AS student_name,t.state,t.reason
            FROM finance_fee_assignment_targets t JOIN admissions a ON a.admission_id=t.admission_id
            WHERE t.run_id=:id ORDER BY t.admission_id LIMIT 20
            """,p,(rs,n)->new Target(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)));
        long ready=((Number)counts.get("ready")).longValue();BigDecimal total=(BigDecimal)r.get("per_student");
        return new Run(id,((Number)r.get("structure_id")).longValue(),(String)r.get("structure_name"),(String)r.get("program"),
            (Integer)r.get("batch_year"),(String)r.get("status"),((java.sql.Timestamp)r.get("expires_at")).toInstant().atOffset(java.time.ZoneOffset.UTC),
            ((Number)counts.get("matched")).longValue(),ready,((Number)counts.get("assigned")).longValue(),
            ((Number)counts.get("skipped")).longValue(),((Number)counts.get("ineligible")).longValue(),total,total.multiply(BigDecimal.valueOf(ready)),sample);
    }
    @Transactional public Run process(UUID id,String actor){
        var p=params(id,actor);
        var rows=jdbc.queryForList("SELECT * FROM finance_fee_assignment_runs WHERE id=:id AND created_by=:actor FOR UPDATE",p);
        if(rows.isEmpty())throw new NoSuchElementException("Assignment run not found");var run=rows.get(0);
        if("COMPLETE".equals(run.get("status")))return get(id,actor);
        if("PREVIEW".equals(run.get("status"))&&((java.sql.Timestamp)run.get("expires_at")).toInstant().isBefore(java.time.Instant.now()))throw new ConflictException("Preview expired. Create a new preview.");
        long structureId=((Number)run.get("structure_id")).longValue();var s=structure(structureId,true);
        p.addValue("structure",structureId);
        jdbc.update("UPDATE finance_fee_assignment_runs SET status='RUNNING' WHERE id=:id",p);
        var targets=jdbc.queryForList("SELECT admission_id FROM finance_fee_assignment_targets WHERE run_id=:id AND state='READY' ORDER BY admission_id LIMIT 25 FOR UPDATE",p,String.class);
        for(String admission:targets){
            p.addValue("admission",admission);
            var admissions=jdbc.queryForList("SELECT status,program,semester,batch_year FROM admissions WHERE admission_id=:admission FOR SHARE",p);
            var a=admissions.get(0);
            // JDBC may represent smallint and integer differently; compare numerically.
            boolean eligible="APPROVED".equals(a.get("status"))&&run.get("program").equals(a.get("program"))&&((Number)s.get("semester")).intValue()==((Number)a.get("semester")).intValue()
                &&(run.get("batch_year")==null||((Number)run.get("batch_year")).intValue()==((Number)a.get("batch_year")).intValue());
            String state,reason;
            if(!eligible){state="INELIGIBLE";reason="Admission eligibility changed after preview";}
            else {
                int inserted=jdbc.update("""
                    INSERT INTO fees(fee_id,admission_id,fee_type,amount,amount_paid,due_date,status,remarks,created_at,updated_at,finance_structure_id,finance_structure_line)
                    SELECT 'FEE-'||substr(replace(CAST(gen_random_uuid() AS text),'-',''),1,26),:admission,l.fee_head,l.amount,0,l.due_date,'PENDING',
                      'Bulk assignment '||CAST(:id AS text)||' / '||l.installment,now(),now(),:structure,l.line_order
                    FROM finance_fee_structure_lines l WHERE l.structure_id=:structure
                    ON CONFLICT(admission_id,finance_structure_id,finance_structure_line) DO NOTHING
                    """,p);
                state=inserted>0?"ASSIGNED":"SKIPPED";reason=inserted>0?null:"Template already assigned";
            }
            p.addValue("state",state).addValue("reason",reason);
            jdbc.update("UPDATE finance_fee_assignment_targets SET state=:state,reason=:reason WHERE run_id=:id AND admission_id=:admission",p);
        }
        jdbc.update("UPDATE finance_fee_assignment_runs SET status='COMPLETE' WHERE id=:id AND NOT EXISTS(SELECT 1 FROM finance_fee_assignment_targets WHERE run_id=:id AND state='READY')",p);
        return get(id,actor);
    }
    @Transactional(readOnly=true) public List<Run> history(long structureId,String actor){
        return jdbc.queryForList("SELECT id FROM finance_fee_assignment_runs WHERE structure_id=:structure AND created_by=:actor ORDER BY created_at DESC LIMIT 10",Map.of("structure",structureId,"actor",actor),UUID.class).stream().map(id->get(id,actor)).toList();
    }
}
