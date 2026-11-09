package com.university.erp.service.finance;
import com.university.erp.entity.Course;
import com.university.erp.entity.finance.FeeStructure;
import com.university.erp.repository.CourseRepository;
import com.university.erp.repository.finance.FeeStructureRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class FeeStructureServiceTest {
 final FeeStructureRepository repo=mock(FeeStructureRepository.class);
 final CourseRepository courses=mock(CourseRepository.class);
 final FeeStructureOptionsService options=mock(FeeStructureOptionsService.class);
 final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc=mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class);
 final FeeStructureService service=new FeeStructureService(repo,courses,options,jdbc);
 FeeStructureService.Line line(String installment,String date){return new FeeStructureService.Line("Tuition",installment,new BigDecimal("100.00"),LocalDate.parse(date));}
 FeeStructureService.Input input(List<FeeStructureService.Line> lines){return new FeeStructureService.Input("Test",1L,2026,1,lines,null,null,null,null);}
 @Test void storesBatchScheduleAndNotes(){
  when(courses.findById(1L)).thenReturn(Optional.of(course()));
  when(repo.saveAndFlush(any())).thenAnswer(call->{FeeStructure s=call.getArgument(0);s.setId(1L);return s;});
  var result=service.create(new FeeStructureService.Input("Test",1L,2026,2,List.of(line("First","2027-01-01")),2026,LocalDate.parse("2026-08-01"),LocalDate.parse("2027-07-31")," Reviewed "),"actor");
  assertEquals(2026,result.structure().batchYear());assertEquals("Reviewed",result.structure().notes());assertEquals(LocalDate.parse("2026-08-01"),result.structure().effectiveFrom());
 }
 @Test void rejectsInvalidScheduleAndOutOfRangeDues(){
  assertThrows(IllegalArgumentException.class,()->service.create(new FeeStructureService.Input("Test",1L,2026,1,List.of(line("First","2027-01-01")),null,LocalDate.parse("2027-02-01"),null,null),"actor"));
  assertThrows(IllegalArgumentException.class,()->service.create(new FeeStructureService.Input("Test",1L,2026,1,List.of(line("First","2027-01-01")),null,LocalDate.parse("2027-02-01"),LocalDate.parse("2027-01-01"),null),"actor"));
  when(courses.findById(1L)).thenReturn(Optional.of(course()));
  assertThrows(IllegalArgumentException.class,()->service.create(new FeeStructureService.Input("Test",1L,2026,1,List.of(line("First","2027-01-01")),null,LocalDate.parse("2027-02-01"),LocalDate.parse("2027-03-01"),null),"actor"));
  verify(repo,never()).saveAndFlush(any());
 }
 Course course(){var c=new Course();c.setId(1L);c.setName("B.Tech");c.setProgram("Engineering");c.setStatus(Course.Status.ACTIVE);return c;}
 @Test void universityPlanNeedsNoCourseAndSupportsThirdPlusFinalYear(){
  when(repo.saveAndFlush(any())).thenAnswer(call->{FeeStructure s=call.getArgument(0);s.setId(10L);return s;});
  var result=service.create(new FeeStructureService.Input("University exam",null,2026,0,List.of(line("First","2027-01-01")),null,null,null,null,"UNIVERSITY",List.of(),List.of(3),true),"actor");
  assertNull(result.structure().courseId());assertEquals("UNIVERSITY",result.structure().courseScope());assertEquals(List.of(3),result.structure().studyYears());assertTrue(result.structure().includeFinalYear());assertEquals(0,result.structure().semester());verifyNoInteractions(courses);
 }
 @Test void selectedCourseScopeValidatesEveryCourse(){
  var second=course();second.setId(2L);second.setName("B.Com");
  when(courses.findAllById(List.of(1L,2L))).thenReturn(List.of(course(),second));
  when(repo.saveAndFlush(any())).thenAnswer(call->{FeeStructure s=call.getArgument(0);s.setId(11L);return s;});
  var result=service.create(new FeeStructureService.Input("Shared fees",null,2026,0,List.of(line("First","2027-01-01")),null,null,null,null,"SELECTED",List.of(1L,2L),List.of(),false),"actor");
  assertEquals(List.of(1L,2L),result.structure().courseIds());assertEquals(2,result.structure().courseNames().size());
  second.setStatus(Course.Status.INACTIVE);
  assertThrows(IllegalArgumentException.class,()->service.create(new FeeStructureService.Input("Shared fees",null,2026,0,List.of(line("First","2027-01-01")),null,null,null,null,"SELECTED",List.of(1L,2L),List.of(),false),"actor"));
 }
 @Test void invalidScopeCombinationsCannotExpandEligibility(){
  for(var input:List.of(
   new FeeStructureService.Input("Invalid",1L,2026,0,List.of(line("First","2027-01-01")),null,null,null,null,"UNIVERSITY",List.of(),List.of(),false),
   new FeeStructureService.Input("Invalid",null,2026,0,List.of(line("First","2027-01-01")),null,null,null,null,"SELECTED",List.of(),List.of(),false),
   new FeeStructureService.Input("Invalid",1L,2026,0,List.of(line("First","2027-01-01")),null,null,null,null,null,null,null,false)))assertThrows(IllegalArgumentException.class,()->service.create(input,"actor"));
  verify(repo,never()).saveAndFlush(any());
 }
 @Test void createsSnapshotWithoutChangingAdminCourseOrStudentFees(){
  when(courses.findById(1L)).thenReturn(Optional.of(course()));
  when(repo.saveAndFlush(any())).thenAnswer(call->{FeeStructure s=call.getArgument(0);s.setId(1L);return s;});
  var result=service.create(input(List.of(line("First","2027-01-01"),line("Second","2027-03-01"))),"accountant@example.invalid");
  assertEquals(new BigDecimal("200.00"),result.total());assertEquals("DRAFT",result.structure().status());assertEquals(2,result.lines().size());
  verify(courses,never()).save(any());
 }
 @Test void rejectsDuplicateHeadsAndConflictingInstallmentDates(){
  when(courses.findById(1L)).thenReturn(Optional.of(course()));
  assertThrows(IllegalArgumentException.class,()->service.create(input(List.of(line("First","2027-01-01"),line("First","2027-01-01"))),"actor"));
  var other=new FeeStructureService.Line("Hostel","First",BigDecimal.TEN,LocalDate.parse("2027-02-01"));
  assertThrows(IllegalArgumentException.class,()->service.create(input(List.of(line("First","2027-01-01"),other)),"actor"));
  verify(repo,never()).saveAndFlush(any());
 }
 @Test void activeCourseRequired(){var c=course();c.setStatus(Course.Status.INACTIVE);when(courses.findById(1L)).thenReturn(Optional.of(c));assertThrows(IllegalArgumentException.class,()->service.create(input(List.of(line("First","2027-01-01"))),"actor"));}
 @Test void publishedSchedulesCannotReturnToDraftAndStaleVersionsFail(){
  var s=new FeeStructure();s.setId(1L);s.setCourse(course());s.setName("Test");s.setAcademicYear(2026);s.setSemester(1);s.setVersion(2);s.setStatus("ACTIVE");
  when(repo.detail(1L)).thenReturn(Optional.of(s));
  assertThrows(com.university.erp.exception.ConflictException.class,()->service.transition(1,new FeeStructureService.Transition(1,"ARCHIVED")));
  assertThrows(IllegalArgumentException.class,()->service.transition(1,new FeeStructureService.Transition(2,"DRAFT")));
  when(repo.saveAndFlush(s)).thenReturn(s);assertEquals("ARCHIVED",service.transition(1,new FeeStructureService.Transition(2,"ARCHIVED")).structure().status());
 }
 @Test void protectsAssignedPlansFromEditingAndDeletion(){
  var s=new FeeStructure();s.setId(9L);s.setVersion(0L);s.setStatus("ARCHIVED");when(repo.detail(9L)).thenReturn(Optional.of(s));
  when(jdbc.queryForObject(anyString(),anyMap(),eq(Long.class))).thenReturn(1L);
  assertThrows(IllegalArgumentException.class,()->service.delete(9,0));
  assertThrows(IllegalArgumentException.class,()->service.update(9,0,input(List.of(line("First","2027-01-01"))),"actor"));
  verify(repo,never()).delete(any());
 }
 @Test void inactivePlanCanReactivateAndUnusedDraftCanDelete(){
  var s=new FeeStructure();s.setId(9L);s.setVersion(0L);s.setCourse(course());s.setStatus("ARCHIVED");when(repo.detail(9L)).thenReturn(Optional.of(s));when(repo.saveAndFlush(s)).thenReturn(s);
  assertEquals("ACTIVE",service.transition(9,new FeeStructureService.Transition(0,"ACTIVE")).structure().status());
  assertThrows(IllegalArgumentException.class,()->service.delete(9,0));s.setStatus("DRAFT");
  assertThrows(com.university.erp.exception.ConflictException.class,()->service.delete(9,2));service.delete(9,0);verify(repo).delete(s);
 }
}
