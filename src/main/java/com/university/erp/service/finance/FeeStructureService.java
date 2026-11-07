package com.university.erp.service.finance;

import com.university.erp.entity.finance.FeeStructure;
import com.university.erp.entity.Course;
import com.university.erp.repository.CourseRepository;
import com.university.erp.repository.finance.FeeStructureRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FeeStructureService {
    private final FeeStructureRepository structures;
    private final CourseRepository courses;
    public record Line(@NotBlank @Size(max=60) String feeHead,@NotBlank @Size(max=60) String installment,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotNull LocalDate dueDate){}
    public record Input(@NotBlank @Size(max=120) String name,@NotNull Long courseId,
        @Min(2000) @Max(2200) int academicYear,@Min(1) @Max(100) int semester,
        @NotEmpty @Size(max=200) List<@NotNull @Valid Line> lines,
        @Min(2000) @Max(2200) Integer batchYear,LocalDate effectiveFrom,LocalDate effectiveUntil,
        @Size(max=1000) String notes){}
    public record Row(long id,long version,String name,long courseId,String courseName,String program,int academicYear,
        int semester,String status,OffsetDateTime updatedAt,Integer batchYear,LocalDate effectiveFrom,
        LocalDate effectiveUntil,String notes){}
    public record Detail(Row structure,List<Line> lines,BigDecimal total,String createdBy){}
    public record CourseOption(long id,String name,String program,List<SourceLine> components){}
    public record SourceLine(String feeHead,BigDecimal amount,String frequency){}
    public record Transition(@Min(0) long version,@NotBlank String status){}
    private Row row(FeeStructure s){return new Row(s.getId(),s.getVersion(),s.getName(),s.getCourse().getId(),s.getCourse().getName(),s.getCourse().getProgram(),s.getAcademicYear(),s.getSemester(),s.getStatus(),s.getUpdatedAt(),s.getBatchYear(),s.getEffectiveFrom(),s.getEffectiveUntil(),s.getNotes());}
    private FeeStructure find(long id){return structures.detail(id).orElseThrow(()->new NoSuchElementException("Fee structure not found"));}
    public Page<Row> list(String search,String status,int page){
        if(!Set.of("","DRAFT","ACTIVE","ARCHIVED").contains(status))throw new IllegalArgumentException("Invalid structure status");
        return structures.search(search.trim(),status,PageRequest.of(page,20,Sort.by("id").descending())).map(this::row);
    }
    public Detail detail(long id){var s=find(id);return detail(s);}
    private Detail detail(FeeStructure s){var lines=s.getLines().stream().map(l->new Line(l.getFeeHead(),l.getInstallment(),l.getAmount(),l.getDueDate())).toList();return new Detail(row(s),lines,lines.stream().map(Line::amount).reduce(BigDecimal.ZERO,BigDecimal::add),s.getCreatedBy());}
    public Page<CourseOption> courses(String search,int page){
        // Reuse existing course catalog and its Admin-maintained fee components.
        return courses.search(search.trim(),"",Course.Status.ACTIVE,PageRequest.of(page,20)).map(c->new CourseOption(c.getId(),c.getName(),c.getProgram(),c.getFeeComponents().stream().map(l->new SourceLine(l.getFeeType(),l.getAmount(),l.getFrequency())).toList()));
    }
    @Transactional public Detail create(Input input,String actor){
        if((input.effectiveFrom()==null)!=(input.effectiveUntil()==null))throw new IllegalArgumentException("Enter both schedule start and end dates");
        if(input.effectiveFrom()!=null&&input.effectiveUntil().isBefore(input.effectiveFrom()))throw new IllegalArgumentException("Schedule end date must be on or after its start date");
        var c=courses.findById(input.courseId()).orElseThrow(()->new NoSuchElementException("Course not found"));
        if(c.getStatus()!=Course.Status.ACTIVE)throw new IllegalArgumentException("Select an active course");
        var s=new FeeStructure();s.setName(input.name().trim());s.setCourse(c);s.setAcademicYear(input.academicYear());s.setSemester(input.semester());s.setCreatedBy(actor);
        s.setBatchYear(input.batchYear());s.setEffectiveFrom(input.effectiveFrom());s.setEffectiveUntil(input.effectiveUntil());s.setNotes(input.notes()==null?null:input.notes().trim());
        Set<String> keys=new HashSet<>();Map<String,LocalDate> dates=new HashMap<>();BigDecimal total=BigDecimal.ZERO;
        for(var l:input.lines()){
            if(input.effectiveFrom()!=null&&(l.dueDate().isBefore(input.effectiveFrom())||l.dueDate().isAfter(input.effectiveUntil())))throw new IllegalArgumentException("Installment due dates must fall within the schedule dates");
            String head=l.feeHead().trim(),installment=l.installment().trim();
            if(!keys.add(head.toLowerCase(Locale.ROOT)+"\u0000"+installment.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate fee head in the same installment");
            LocalDate previous=dates.putIfAbsent(installment.toLowerCase(Locale.ROOT),l.dueDate());
            if(previous!=null&&!previous.equals(l.dueDate()))throw new IllegalArgumentException("All lines in an installment must have the same due date");
            total=total.add(l.amount());s.getLines().add(new FeeStructure.Line(head,installment,l.amount(),l.dueDate()));
        }
        if(total.compareTo(new BigDecimal("9999999999.99"))>0)throw new IllegalArgumentException("Structure total exceeds supported amount");
        return detail(structures.saveAndFlush(s));
    }
    @Transactional public Detail transition(long id,Transition input){
        var s=find(id);if(s.getVersion()!=input.version())throw new com.university.erp.exception.ConflictException("Structure changed. Refresh and retry.");
        if(!(s.getStatus().equals("DRAFT")&&input.status().equals("ACTIVE"))&&!(s.getStatus().equals("ACTIVE")&&input.status().equals("ARCHIVED")))throw new IllegalArgumentException("Allowed transitions: Draft to Active, Active to Archived");
        s.setStatus(input.status());return detail(structures.saveAndFlush(s));
    }
}
