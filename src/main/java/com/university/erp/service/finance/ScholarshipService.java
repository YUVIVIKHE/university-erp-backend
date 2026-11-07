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
    public record Review(@NotBlank String status,@NotBlank @Size(max=2000) String note){}
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
    private static final String SELECT="SELECT s.*,d.outcome AS financial_outcome,d.amount AS approved_amount,a.first_name||' '||a.last_name AS student_name,a.program FROM finance_scholarship_applications s JOIN admissions a ON a.admission_id=s.admission_id LEFT JOIN finance_award_decisions d ON d.application_id=s.id ";
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
    @Transactional public Map<String,Object> review(UUID id,Review review,String actor){
        var rows=jdbc.queryForList("SELECT * FROM finance_scholarship_applications WHERE id=:id FOR UPDATE",Map.of("id",id));
        if(rows.isEmpty())throw new NoSuchElementException("Application not found");
        var current=rows.get(0);validateReview(review,(String)current.get("created_by"),actor);
        // Exact retry of a terminal decision is harmless; competing decisions cannot overwrite it.
        if(!current.get("status").equals("SUBMITTED")){
            if(current.get("status").equals(review.status())&&actor.equals(current.get("reviewed_by"))&&review.note().trim().equals(current.get("review_note")))return get(id);
            throw new ConflictException("This application has already been reviewed or withdrawn");
        }
        jdbc.update("UPDATE finance_scholarship_applications SET status=:status,reviewed_by=:actor,reviewed_at=now(),review_note=:note WHERE id=:id",
            Map.of("id",id,"status",review.status(),"actor",actor,"note",review.note().trim()));
        return get(id);
    }
}
