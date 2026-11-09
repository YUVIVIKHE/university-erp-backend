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
    private final FeeStructureOptionsService options;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    public record Line(@NotBlank @Size(max=60) String feeHead,@NotBlank @Size(max=60) String installment,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotNull LocalDate dueDate){}
    public record Input(@NotBlank @Size(max=120) String name,Long courseId,
        @Min(2000) @Max(2200) int academicYear,@Min(0) @Max(100) int semester,
        @NotEmpty @Size(max=200) List<@NotNull @Valid Line> lines,
        @Min(2000) @Max(2200) Integer batchYear,LocalDate effectiveFrom,LocalDate effectiveUntil,
        @Size(max=1000) String notes,
        @Pattern(regexp="LEGACY|SELECTED|UNIVERSITY") String courseScope,
        @Size(max=200) List<@NotNull @Positive Long> courseIds,
        @Size(max=50) List<@NotNull @Min(1) @Max(50) Integer> studyYears,Boolean includeFinalYear,
        @Size(max=100) List<@NotNull @Min(1) @Max(100) Integer> semesters,
        @Size(max=201) List<@NotNull @Min(2000) @Max(2200) Integer> batchYears){
        public Input(String name,Long courseId,int academicYear,int semester,List<Line> lines,Integer batchYear,LocalDate effectiveFrom,LocalDate effectiveUntil,String notes,String courseScope,List<Long> courseIds,List<Integer> studyYears,Boolean includeFinalYear){this(name,courseId,academicYear,semester,lines,batchYear,effectiveFrom,effectiveUntil,notes,courseScope,courseIds,studyYears,includeFinalYear,null,null);}
        public Input(String name,Long courseId,int academicYear,int semester,List<Line> lines,Integer batchYear,LocalDate effectiveFrom,LocalDate effectiveUntil,String notes){this(name,courseId,academicYear,semester,lines,batchYear,effectiveFrom,effectiveUntil,notes,null,null,null,false);}
    }
    public record Row(long id,long version,String name,Long courseId,String courseName,String program,int academicYear,
        int semester,String status,OffsetDateTime updatedAt,Integer batchYear,LocalDate effectiveFrom,
        LocalDate effectiveUntil,String notes,String courseScope,List<Long> courseIds,List<String> courseNames,List<Integer> studyYears,boolean includeFinalYear,List<Integer> semesters,List<Integer> batchYears){}
    public record Detail(Row structure,List<Line> lines,BigDecimal total,String createdBy){}
    public record CourseOption(long id,String name,String program,List<SourceLine> components,String code,Integer durationMonths){}
    public record SourceLine(String feeHead,BigDecimal amount,String frequency){}
    public record Transition(@Min(0) long version,@NotBlank String status){}
    private Row row(FeeStructure s){
        var selected=s.getSelectedCourses().stream().sorted(Comparator.comparing(Course::getId)).toList();
        String label=s.getCourseScope().equals("UNIVERSITY")?"Whole university":s.getCourseScope().equals("SELECTED")?selected.stream().map(Course::getName).collect(java.util.stream.Collectors.joining(", ")):s.getCourse().getName();
        return new Row(s.getId(),s.getVersion(),s.getName(),s.getCourse()==null?null:s.getCourse().getId(),label,s.getCourse()==null?"All admission programs":s.getCourse().getProgram(),s.getAcademicYear(),s.getSemester(),s.getStatus(),s.getUpdatedAt(),s.getBatchYear(),s.getEffectiveFrom(),s.getEffectiveUntil(),s.getNotes(),s.getCourseScope(),selected.stream().map(Course::getId).toList(),selected.stream().map(Course::getName).toList(),s.getStudyYears().stream().sorted().toList(),s.isIncludeFinalYear(),s.getSemesters().stream().sorted().toList(),s.getBatchYears().stream().sorted().toList());
    }
    private FeeStructure find(long id){return structures.detail(id).orElseThrow(()->new NoSuchElementException("Fee structure not found"));}
    public Page<Row> list(String search,String status,int page){
        if(!Set.of("","DRAFT","ACTIVE","ARCHIVED").contains(status))throw new IllegalArgumentException("Invalid structure status");
        return structures.search(search.trim(),status,PageRequest.of(page,20,Sort.by("id").descending())).map(this::row);
    }
    public Detail detail(long id){var s=find(id);return detail(s);}
    private Detail detail(FeeStructure s){var lines=s.getLines().stream().map(l->new Line(l.getFeeHead(),l.getInstallment(),l.getAmount(),l.getDueDate())).toList();return new Detail(row(s),lines,lines.stream().map(Line::amount).reduce(BigDecimal.ZERO,BigDecimal::add),s.getCreatedBy());}
    public Page<CourseOption> courses(String search,int page){
        // Reuse existing course catalog and its Admin-maintained fee components.
        return courses.search(search.trim(),"",Course.Status.ACTIVE,PageRequest.of(page,20)).map(c->new CourseOption(c.getId(),c.getName(),c.getProgram(),c.getFeeComponents().stream().map(l->new SourceLine(l.getFeeType(),l.getAmount(),l.getFrequency())).toList(),c.getCourseCode(),c.getDurationMonths()));
    }
    @Transactional public Detail create(Input input,String actor){return detail(structures.saveAndFlush(build(input,actor)));}
    private FeeStructure build(Input input,String actor){
        if((input.effectiveFrom()==null)!=(input.effectiveUntil()==null))throw new IllegalArgumentException("Enter both schedule start and end dates");
        if(input.effectiveFrom()!=null&&input.effectiveUntil().isBefore(input.effectiveFrom()))throw new IllegalArgumentException("Schedule end date must be on or after its start date");
        String scope=input.courseScope()==null?"LEGACY":input.courseScope();
        if(!Set.of("LEGACY","SELECTED","UNIVERSITY").contains(scope))throw new IllegalArgumentException("Invalid course scope");
        var selected=new LinkedHashSet<Course>();Course c=null;
        if(scope.equals("LEGACY")){
            if(input.courseId()==null||input.semester()<1)throw new IllegalArgumentException("Select a course and semester");
            if((input.courseIds()!=null&&!input.courseIds().isEmpty())||(input.studyYears()!=null&&!input.studyYears().isEmpty())||Boolean.TRUE.equals(input.includeFinalYear()))throw new IllegalArgumentException("Use selected-course or university scope for year targeting");
            c=courses.findById(input.courseId()).orElseThrow(()->new NoSuchElementException("Course not found"));
            if(c.getStatus()!=Course.Status.ACTIVE)throw new IllegalArgumentException("Select an active course");
        }else if(scope.equals("SELECTED")){
            if(input.courseIds()==null||input.courseIds().isEmpty()||new HashSet<>(input.courseIds()).size()!=input.courseIds().size())throw new IllegalArgumentException("Choose distinct active courses");
            var found=courses.findAllById(input.courseIds());
            if(found.size()!=input.courseIds().size()||found.stream().anyMatch(x->x.getStatus()!=Course.Status.ACTIVE))throw new IllegalArgumentException("Choose existing active courses");
            found.stream().sorted(Comparator.comparing(Course::getId)).forEach(selected::add);c=selected.iterator().next();
        }else if(input.courseId()!=null||(input.courseIds()!=null&&!input.courseIds().isEmpty()))throw new IllegalArgumentException("University scope must not include selected courses");
        if(input.studyYears()!=null&&new HashSet<>(input.studyYears()).size()!=input.studyYears().size())throw new IllegalArgumentException("Choose distinct study years");
        var semesterList=input.semesters()==null?List.<Integer>of():input.semesters();
        var batchList=input.batchYears()==null?List.<Integer>of():input.batchYears();
        if(new HashSet<>(semesterList).size()!=semesterList.size()||new HashSet<>(batchList).size()!=batchList.size())throw new IllegalArgumentException("Choose distinct semesters and batches");
        if((input.semester()>0&&!semesterList.isEmpty())||(input.batchYear()!=null&&!batchList.isEmpty()))throw new IllegalArgumentException("Use either the single or multi-value cohort filter, not both");
        if(scope.equals("LEGACY")&&(!semesterList.isEmpty()||!batchList.isEmpty()))throw new IllegalArgumentException("Use a new scoped plan for multi-semester or multi-batch filters");
        if(!scope.equals("LEGACY"))options.validate(scope.equals("SELECTED")?input.courseIds():List.of(),input.studyYears()==null?List.of():input.studyYears(),Boolean.TRUE.equals(input.includeFinalYear()),input.semester()>0?List.of(input.semester()):semesterList,input.batchYear()!=null?List.of(input.batchYear()):batchList);
        var s=new FeeStructure();s.setName(input.name().trim());s.setCourse(c);s.setCourseScope(scope);s.setSelectedCourses(selected);s.setIncludeFinalYear(Boolean.TRUE.equals(input.includeFinalYear()));
        if(input.studyYears()!=null)s.getStudyYears().addAll(input.studyYears());
        s.getSemesters().addAll(semesterList);s.getBatchYears().addAll(batchList);
        s.setAcademicYear(input.academicYear());s.setSemester(input.semester());s.setCreatedBy(actor);
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
        return s;
    }
    private FeeStructure locked(long id,long version){
        jdbc.queryForList("SELECT id FROM finance_fee_structures WHERE id=:id FOR UPDATE",Map.of("id",id));
        var s=find(id);if(s.getVersion()!=version)throw new com.university.erp.exception.ConflictException("Structure changed. Refresh and retry.");return s;
    }
    private void unused(long id){
        Long count=jdbc.queryForObject("SELECT (SELECT count(*) FROM finance_fee_assignment_runs WHERE structure_id=:id)+(SELECT count(*) FROM fees WHERE finance_structure_id=:id)",Map.of("id",id),Long.class);
        if(count!=null&&count>0)throw new IllegalArgumentException("This structure has assignment history. Deactivate it and create a new plan instead of editing or deleting it.");
    }
    @Transactional public Detail update(long id,long version,Input input,String actor){
        var s=locked(id,version);if(s.getStatus().equals("ACTIVE"))throw new IllegalArgumentException("Deactivate the structure before editing");unused(id);var fresh=build(input,actor);
        s.setName(fresh.getName());s.setCourse(fresh.getCourse());s.setCourseScope(fresh.getCourseScope());s.getSelectedCourses().clear();s.getSelectedCourses().addAll(fresh.getSelectedCourses());s.getStudyYears().clear();s.getStudyYears().addAll(fresh.getStudyYears());s.getSemesters().clear();s.getSemesters().addAll(fresh.getSemesters());s.getBatchYears().clear();s.getBatchYears().addAll(fresh.getBatchYears());s.setIncludeFinalYear(fresh.isIncludeFinalYear());s.setAcademicYear(fresh.getAcademicYear());s.setSemester(fresh.getSemester());s.setBatchYear(fresh.getBatchYear());s.setEffectiveFrom(fresh.getEffectiveFrom());s.setEffectiveUntil(fresh.getEffectiveUntil());s.setNotes(fresh.getNotes());s.getLines().clear();s.getLines().addAll(fresh.getLines());
        return detail(structures.saveAndFlush(s));
    }
    @Transactional public void delete(long id,long version){var s=locked(id,version);if(s.getStatus().equals("ACTIVE"))throw new IllegalArgumentException("Deactivate the structure before deleting");unused(id);structures.delete(s);structures.flush();}
    @Transactional public Detail transition(long id,Transition input){
        var s=locked(id,input.version());
        if(!(s.getStatus().equals("DRAFT")&&input.status().equals("ACTIVE"))&&!(s.getStatus().equals("ACTIVE")&&input.status().equals("ARCHIVED"))&&!(s.getStatus().equals("ARCHIVED")&&input.status().equals("ACTIVE")))throw new IllegalArgumentException("Allowed transitions: Draft to Active, Active to Inactive, Inactive to Active");
        s.setStatus(input.status());return detail(structures.saveAndFlush(s));
    }
}
