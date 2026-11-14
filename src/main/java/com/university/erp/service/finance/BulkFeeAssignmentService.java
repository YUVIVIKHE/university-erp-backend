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
    public record Selection(@Size(max=100) String program,@Min(2000) @Max(2200) Integer batchYear,
        @Size(max=100) String search,@Size(max=200) List<@NotBlank @Size(max=30) String> admissionIds,
        @Size(max=201) List<@NotNull @Min(2000) @Max(2200) Integer> batchYears) {
        public Selection(String program,Integer batchYear,String search,List<String> admissionIds){this(program,batchYear,search,admissionIds,null);}
    }
    public record Target(String admissionId,String studentName,String state,String reason) {}
    public record Run(UUID id,long structureId,String structureName,String program,Integer batchYear,String status,
        OffsetDateTime expiresAt,long matched,long ready,long assigned,long skipped,long ineligible,
        BigDecimal perStudent,BigDecimal previewAmount,List<Target> sample,long mappingUnresolved,List<Integer> batchYears) {}
    private MapSqlParameterSource params(UUID id,String actor){return new MapSqlParameterSource("id",id).addValue("actor",actor);}
    private Map<String,Object> structure(long id,boolean lock){
        var rows=jdbc.queryForList("SELECT * FROM finance_fee_structures WHERE id=:id"+(lock?" FOR SHARE":""),Map.of("id",id));
        if(rows.isEmpty())throw new NoSuchElementException("Fee structure not found");
        var s=rows.get(0);if(!"ACTIVE".equals(s.get("status")))throw new IllegalArgumentException("Publish an active structure before assigning fees");return s;
    }
    public List<String> programs(String search){return jdbc.queryForList("SELECT DISTINCT program FROM admissions WHERE status='APPROVED' AND lower(program) LIKE lower(:search) ORDER BY program LIMIT 50",Map.of("search","%"+search.trim()+"%"),String.class);}
    static String mappingJoin(){return """
        LEFT JOIN LATERAL (
          SELECT count(*) AS matches,min(c.id) AS course_id,min(c.duration_months) AS duration_months
          FROM courses c WHERE c.status='ACTIVE' AND (
            lower(btrim(c.name))=lower(btrim(a.program)) OR
            (lower(btrim(c.program))=lower(btrim(a.program)) AND NOT EXISTS(
              SELECT 1 FROM courses named WHERE named.status='ACTIVE' AND lower(btrim(named.name))=lower(btrim(a.program)))))
        ) m ON true
        """;}
    private static String cohortFilter(){return """
        AND (NOT EXISTS(SELECT 1 FROM finance_fee_structure_semesters sm WHERE sm.structure_id=:structure)
          OR EXISTS(SELECT 1 FROM finance_fee_structure_semesters sm WHERE sm.structure_id=:structure AND sm.semester=a.semester))
        AND (NOT EXISTS(SELECT 1 FROM finance_fee_structure_batches b WHERE b.structure_id=:structure)
          OR EXISTS(SELECT 1 FROM finance_fee_structure_batches b WHERE b.structure_id=:structure AND b.batch_year=a.batch_year))
        AND (NOT EXISTS(SELECT 1 FROM finance_fee_assignment_run_batches rb WHERE rb.run_id=:id)
          OR EXISTS(SELECT 1 FROM finance_fee_assignment_run_batches rb WHERE rb.run_id=:id AND rb.batch_year=a.batch_year))
        """;}
    private static String scopeFilter(Map<String,Object> s){
        String course="SELECTED".equals(s.get("course_scope"))?"m.matches=1 AND EXISTS(SELECT 1 FROM finance_fee_structure_courses sc WHERE sc.structure_id=:structure AND sc.course_id=m.course_id)":"true";
        String years="((NOT EXISTS(SELECT 1 FROM finance_fee_structure_years y WHERE y.structure_id=:structure) AND NOT "+Boolean.TRUE.equals(s.get("include_final_year"))+") OR EXISTS(SELECT 1 FROM finance_fee_structure_years y WHERE y.structure_id=:structure AND y.study_year=ceil(a.semester/2.0))";
        if(Boolean.TRUE.equals(s.get("include_final_year")))years+=" OR (m.matches=1 AND m.duration_months>0 AND mod(m.duration_months,12)=0 AND ceil(a.semester/2.0)=m.duration_months/12)";
        return "("+course+") AND "+years+")";
    }
    @Transactional public Run preview(long structureId,Selection selection,String actor){
        var s=structure(structureId,true);Integer batch=selection.batchYear();
        if(s.get("batch_year")!=null){int required=((Number)s.get("batch_year")).intValue();if(batch!=null&&batch!=required)throw new IllegalArgumentException("Batch must match the structure eligibility");batch=required;}
        var batches=selection.batchYears()==null?List.<Integer>of():selection.batchYears();
        if(new HashSet<>(batches).size()!=batches.size()||(!batches.isEmpty()&&selection.batchYear()!=null))throw new IllegalArgumentException("Choose distinct batches and use one batch filter format");
        var allowed=jdbc.queryForList("SELECT batch_year FROM finance_fee_structure_batches WHERE structure_id=:id",Map.of("id",structureId),Integer.class);
        if(!batches.isEmpty()&&((s.get("batch_year")!=null&&batches.stream().anyMatch(b->!b.equals(((Number)s.get("batch_year")).intValue())))||(!allowed.isEmpty()&&!allowed.containsAll(batches))))throw new IllegalArgumentException("Batch selection must stay within this plan's allowed batches");
        if(batch!=null&&!allowed.isEmpty()&&!allowed.contains(batch))throw new IllegalArgumentException("Batch must match the structure eligibility");
        String scope=(String)s.get("course_scope");boolean legacy=scope.equals("LEGACY");
        String program=selection.program()==null?"":selection.program().trim();
        if(legacy&&program.isEmpty())throw new IllegalArgumentException("Select an existing approved-admission program");
        if(!program.isEmpty()&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM admissions WHERE program=:program AND status='APPROVED')",Map.of("program",program),Boolean.class)))throw new IllegalArgumentException("Select an existing approved-admission program");
        UUID id=UUID.randomUUID();var p=params(id,actor).addValue("structure",structureId).addValue("program",program).addValue("batch",batch)
            .addValue("semester",s.get("semester")).addValue("search","%"+(selection.search()==null?"":selection.search().trim())+"%");
        boolean all=selection.admissionIds()==null||selection.admissionIds().isEmpty();
        p.addValue("all",all).addValue("selected",all?List.of("__none__"):selection.admissionIds());
        String base="a.status='APPROVED' AND (:program='' OR a.program=:program) AND (:semester=0 OR a.semester=:semester) AND (CAST(:batch AS integer) IS NULL OR a.batch_year=:batch) AND (lower(a.first_name||' '||a.last_name) LIKE lower(:search) OR lower(a.admission_id) LIKE lower(:search)) AND (:all OR a.admission_id IN (:selected))";
        long unresolved=legacy?0:jdbc.queryForObject("SELECT count(*) FROM admissions a "+mappingJoin()+" WHERE "+base+" AND m.matches<>1",p,Long.class);
        p.addValue("unresolved",unresolved);
        jdbc.update("INSERT INTO finance_fee_assignment_runs(id,structure_id,created_by,program,batch_year,mapping_unresolved) VALUES(:id,:structure,:actor,:program,:batch,:unresolved)",p);
        for(int selectedBatch:batches){p.addValue("selectedBatch",selectedBatch);jdbc.update("INSERT INTO finance_fee_assignment_run_batches(run_id,batch_year) VALUES(:id,:selectedBatch)",p);}
        jdbc.update("""
            INSERT INTO finance_fee_assignment_targets(run_id,admission_id,state,reason)
            SELECT :id,a.admission_id,
              CASE WHEN EXISTS(SELECT 1 FROM fees f WHERE f.admission_id=a.admission_id AND f.finance_structure_id=:structure) THEN 'SKIPPED' ELSE 'READY' END,
              CASE WHEN EXISTS(SELECT 1 FROM fees f WHERE f.admission_id=a.admission_id AND f.finance_structure_id=:structure) THEN 'Template already assigned' ELSE NULL END
            FROM admissions a
            """+(legacy?"":mappingJoin())+" WHERE "+base+cohortFilter()+(legacy?"":" AND "+scopeFilter(s)),p);
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
        return new Run(id,((Number)r.get("structure_id")).longValue(),(String)r.get("structure_name"),((String)r.get("program")).isEmpty()?"All eligible programs":(String)r.get("program"),
            (Integer)r.get("batch_year"),(String)r.get("status"),((java.sql.Timestamp)r.get("expires_at")).toInstant().atOffset(java.time.ZoneOffset.UTC),
            ((Number)counts.get("matched")).longValue(),ready,((Number)counts.get("assigned")).longValue(),
            ((Number)counts.get("skipped")).longValue(),((Number)counts.get("ineligible")).longValue(),total,total.multiply(BigDecimal.valueOf(ready)),sample,((Number)r.get("mapping_unresolved")).longValue(),jdbc.queryForList("SELECT batch_year FROM finance_fee_assignment_run_batches WHERE run_id=:id ORDER BY batch_year",p,Integer.class));
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
            boolean legacy="LEGACY".equals(s.get("course_scope"));
            p.addValue("semester",s.get("semester")).addValue("batch",run.get("batch_year")).addValue("program",run.get("program"));
            var admissions=jdbc.queryForList("SELECT a.admission_id FROM admissions a "+(legacy?"":mappingJoin())+" WHERE a.admission_id=:admission AND a.status='APPROVED' AND (:program='' OR a.program=:program) AND (:semester=0 OR a.semester=:semester) AND (CAST(:batch AS integer) IS NULL OR a.batch_year=:batch)"+cohortFilter()+(legacy?"":" AND "+scopeFilter(s))+" FOR SHARE OF a",p);
            boolean eligible=!admissions.isEmpty();
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
