package com.university.erp.service.finance;

import com.university.erp.exception.ConflictException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class ScholarshipService {
    private final NamedParameterJdbcTemplate jdbc;
    public record Input(@NotNull UUID requestId,@NotBlank @Size(max=30) String admissionId,
        @NotBlank @Size(max=150) String scheme,@NotBlank String category,@NotBlank String funding,
        @Min(2000) @Max(9999) int academicYear,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal requestedAmount,
        @NotBlank @Size(max=250) String evidenceReference,@NotBlank @Size(max=2000) String reason){}
    public record Edit(@Min(0) long version,@NotNull @jakarta.validation.Valid Input application,@NotBlank @Size(max=2000) String note){}
    public record Review(@NotBlank String status,@NotBlank @Size(max=2000) String note,@Min(0) Long version){public Review(String status,String note){this(status,note,null);}}
    static void validate(Input input){
        if(!Set.of("SCHOLARSHIP","CONCESSION").contains(input.category()))throw new IllegalArgumentException("Invalid application category");
        if(!Set.of("INSTITUTION","EXTERNAL").contains(input.funding()))throw new IllegalArgumentException("Invalid funding source");
        if(input.category().equals("CONCESSION")&&!input.funding().equals("INSTITUTION"))throw new IllegalArgumentException("Concessions must be institution-funded");
    }
    static void validateReview(Review review,String creator,String actor){
        if(!Set.of("VERIFIED","REJECTED","CANCELLED").contains(review.status()))throw new IllegalArgumentException("Invalid review status");
        if(review.status().equals("CANCELLED")){
            if(!creator.equals(actor))throw new ConflictException("Only the creator can withdraw this application");
        }else if(creator.equals(actor))throw new ConflictException("Another finance staff member must review your application");
    }
    private static final String SELECT="SELECT s.*,CASE WHEN EXISTS(SELECT 1 FROM finance_award_reversals r WHERE r.decision_id=d.id AND r.status='APPROVED') THEN 'REVERSED' ELSE d.outcome END AS financial_outcome,d.amount AS approved_amount,a.first_name||' '||a.last_name AS student_name,a.program FROM finance_scholarship_applications s JOIN admissions a ON a.admission_id=s.admission_id LEFT JOIN finance_award_decisions d ON d.application_id=s.id ";
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Map<String,Object>> list(String search,String status,int page){
        if(!Set.of("","SUBMITTED","VERIFIED","REJECTED","CANCELLED").contains(status))throw new IllegalArgumentException("Invalid application status");
        var p=new MapSqlParameterSource("search","%"+search.trim()+"%").addValue("status",status).addValue("offset",page*20L);
        String where=" WHERE (:status='' OR s.status=:status) AND (s.admission_id ILIKE :search OR s.scheme ILIKE :search OR a.first_name||' '||a.last_name ILIKE :search) ";
        long total=jdbc.queryForObject("SELECT count(*) FROM finance_scholarship_applications s JOIN admissions a ON a.admission_id=s.admission_id"+where,p,Long.class);
        return new PageImpl<>(jdbc.queryForList(SELECT+where+" ORDER BY s.created_at DESC,s.id LIMIT 20 OFFSET :offset",p),PageRequest.of(page,20),total);
    }
    private Map<String,Object> get(UUID id){return jdbc.queryForMap(SELECT+" WHERE s.id=:id",Map.of("id",id));}
    @Transactional public Map<String,Object> create(Input input,String actor){
        validate(input);
        // Serializes retries of one request, including a different payload using that key.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))",Map.of("key",input.requestId().toString()),Object.class);
        var p=new MapSqlParameterSource("request",input.requestId()).addValue("admission",input.admissionId().trim())
            .addValue("scheme",input.scheme().trim()).addValue("category",input.category()).addValue("funding",input.funding())
            .addValue("year",input.academicYear()).addValue("amount",input.requestedAmount()).addValue("evidence",input.evidenceReference().trim())
            .addValue("reason",input.reason().trim()).addValue("actor",actor).addValue("id",UUID.randomUUID());
        var previous=jdbc.queryForList("SELECT * FROM finance_scholarship_applications WHERE request_id=:request",p);
        if(!previous.isEmpty()){
            var matching=jdbc.queryForList("""
                SELECT id FROM finance_scholarship_applications WHERE request_id=:request AND admission_id=:admission AND scheme=:scheme
                  AND category=:category AND funding=:funding AND academic_year=:year AND requested_amount=:amount
                  AND evidence_reference=:evidence AND reason=:reason AND created_by=:actor
                """,p);
            if(matching.isEmpty())throw new ConflictException("Request ID already used for a different application");
            return get((UUID)matching.get(0).get("id"));
        }
        var admission=jdbc.queryForList("SELECT status FROM admissions WHERE admission_id=:admission FOR SHARE",p);
        if(admission.isEmpty())throw new NoSuchElementException("Admission not found");
        if(!admission.get(0).get("status").equals("APPROVED"))throw new ConflictException("Select an approved admission");
        int count=jdbc.update("""
            INSERT INTO finance_scholarship_applications(id,request_id,admission_id,scheme,category,funding,academic_year,requested_amount,evidence_reference,reason,created_by)
            VALUES(:id,:request,:admission,:scheme,:category,:funding,:year,:amount,:evidence,:reason,:actor) ON CONFLICT DO NOTHING
            """,p);
        if(count==0)throw new ConflictException("An open application already exists for this student, scheme, year and category");
        return get((UUID)p.getValue("id"));
    }
    @Transactional public Map<String,Object> edit(UUID id,Edit input,String actor){
        var p=new MapSqlParameterSource("id",id).addValue("actor",actor).addValue("note",input.note().trim());
        var rows=jdbc.queryForList("SELECT * FROM finance_scholarship_applications WHERE id=:id FOR UPDATE",p);
        if(rows.isEmpty())throw new NoSuchElementException("Application not found");var current=rows.get(0);
        if(!actor.equals(current.get("created_by")))throw new ConflictException("Only the submitter can edit this application");
        if(((Number)current.get("version")).longValue()!=input.version())throw new ConflictException("Application changed. Refresh before editing.");
        if(!Set.of("SUBMITTED","VERIFIED").contains(current.get("status"))||Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM finance_award_decisions WHERE application_id=:id)",p,Boolean.class)))throw new ConflictException("Final applications cannot be edited. Request reversal of a posted award instead.");
        var a=input.application();validate(a);if(!current.get("admission_id").equals(a.admissionId().trim()))throw new IllegalArgumentException("Student identity cannot be changed; withdraw and create a new application");
        jdbc.update("INSERT INTO finance_scholarship_edits(application_id,previous_version,previous_values,changed_by,note) SELECT id,version,to_jsonb(s),:actor,:note FROM finance_scholarship_applications s WHERE id=:id",p);
        p.addValue("scheme",a.scheme().trim()).addValue("category",a.category()).addValue("funding",a.funding()).addValue("year",a.academicYear()).addValue("amount",a.requestedAmount()).addValue("evidence",a.evidenceReference().trim()).addValue("reason",a.reason().trim());
        jdbc.update("UPDATE finance_scholarship_applications SET scheme=:scheme,category=:category,funding=:funding,academic_year=:year,requested_amount=:amount,evidence_reference=:evidence,reason=:reason,status='SUBMITTED',reviewed_by=NULL,reviewed_at=NULL,review_note=NULL,version=version+1 WHERE id=:id",p);
        return get(id);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> edits(UUID id){get(id);return jdbc.queryForList("SELECT previous_version,previous_values->>'scheme' AS previous_scheme,previous_values->>'requested_amount' AS previous_amount,changed_by,note,created_at FROM finance_scholarship_edits WHERE application_id=:id ORDER BY id DESC",Map.of("id",id));}
    @Transactional public Map<String,Object> review(UUID id,Review review,String actor){
        var rows=jdbc.queryForList("SELECT * FROM finance_scholarship_applications WHERE id=:id FOR UPDATE",Map.of("id",id));
        if(rows.isEmpty())throw new NoSuchElementException("Application not found");
        var current=rows.get(0);if(review.version()!=null&&((Number)current.get("version")).longValue()!=review.version())throw new ConflictException("Application changed. Refresh before reviewing.");validateReview(review,(String)current.get("created_by"),actor);
        // Exact retry of a terminal decision is harmless; competing decisions cannot overwrite it.
        if(!current.get("status").equals("SUBMITTED")){
            if(current.get("status").equals(review.status())&&actor.equals(current.get("reviewed_by"))&&review.note().trim().equals(current.get("review_note")))return get(id);
            throw new ConflictException("This application has already been reviewed or withdrawn");
        }
        jdbc.update("UPDATE finance_scholarship_applications SET version=version+1,status=:status,reviewed_by=:actor,reviewed_at=now(),review_note=:note WHERE id=:id",
            Map.of("id",id,"status",review.status(),"actor",actor,"note",review.note().trim()));
        return get(id);
    }
}
