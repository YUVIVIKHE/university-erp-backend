package com.university.erp.service.finance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.IntStream;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FeeStructureOptionsService {
 private final NamedParameterJdbcTemplate jdbc;
 public record Options(List<Integer> semesters,List<Integer> studyYears,List<Integer> finalSemesters,List<Integer> batchYears,long activeCourses,long unconfiguredCourses){}
 static List<Integer> range(int max){return IntStream.rangeClosed(1,max).boxed().toList();}
 public Options read(List<Long> courseIds){
  var ids=courseIds==null?List.<Long>of():courseIds;
  if(ids.size()>200||ids.stream().anyMatch(x->x==null||x<=0)||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Choose distinct active courses");
  var p=new MapSqlParameterSource("all",ids.isEmpty()).addValue("courses",ids.isEmpty()?List.of(-1L):ids);
  var durations=jdbc.queryForList("SELECT duration_months FROM courses WHERE status='ACTIVE' AND (:all OR id IN (:courses))",p,Integer.class);
  if(!ids.isEmpty()&&durations.size()!=ids.size())throw new IllegalArgumentException("A selected course is unavailable. Refresh the course list.");
  int max=0;long unknown=0;var finals=new TreeSet<Integer>();
  for(Integer duration:durations){
   if(duration==null||duration<=0||duration>600){unknown++;continue;}
   int semesters=(duration+5)/6;max=Math.max(max,semesters);
   if(duration%12==0){finals.add(semesters);if(semesters>1)finals.add(semesters-1);}
  }
  String mapping=ids.isEmpty()?"":BulkFeeAssignmentService.mappingJoin();
  String filter=ids.isEmpty()?"":" AND m.matches=1 AND m.course_id IN (:courses)";
  var batches=jdbc.queryForList("SELECT DISTINCT a.batch_year::integer FROM admissions a "+mapping+" WHERE a.status='APPROVED' AND a.batch_year BETWEEN 2000 AND 2200"+filter+" ORDER BY 1 DESC",p,Integer.class);
  return new Options(range(max),range((max+1)/2),List.copyOf(finals),batches,durations.size(),unknown);
 }
 public void validate(List<Long> ids,List<Integer> years,boolean finalYear,List<Integer> semesters,List<Integer> batches){
  var options=read(ids);
  if(!options.studyYears().containsAll(years)||!options.semesters().containsAll(semesters))throw new IllegalArgumentException("Choose study years and semesters supported by the selected course durations");
  if(!options.batchYears().containsAll(batches))throw new IllegalArgumentException("Choose existing approved admission batches for the selected courses");
  if(!years.isEmpty()||finalYear){
   for(int semester:semesters)if(!years.contains((semester+1)/2)&&!(finalYear&&options.finalSemesters().contains(semester)))throw new IllegalArgumentException("Selected semesters do not belong to the selected study years or final year");
  }
  if(finalYear&&options.finalSemesters().isEmpty())throw new IllegalArgumentException("Configure a whole-year course duration before choosing final year");
 }
}
