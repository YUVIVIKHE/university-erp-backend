package com.university.erp.service.finance;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FeeStructureOptionsServiceTest {
 final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
 final FeeStructureOptionsService service=new FeeStructureOptionsService(jdbc);
 void catalog(Integer... durations){
  when(jdbc.queryForList(contains("SELECT duration_months"),any(MapSqlParameterSource.class),eq(Integer.class))).thenReturn(Arrays.asList(durations));
  when(jdbc.queryForList(contains("SELECT DISTINCT"),any(MapSqlParameterSource.class),eq(Integer.class))).thenReturn(List.of(2026,2024));
 }
 @Test void derivesTermsFromSelectedCourseDurations(){
  catalog(36);var three=service.read(List.of(1L));assertEquals(6,three.semesters().size());assertEquals(List.of(1,2,3),three.studyYears());assertEquals(List.of(5,6),three.finalSemesters());
  catalog(36,48);var both=service.read(List.of(1L,2L));assertEquals(8,both.semesters().size());assertEquals(List.of(5,6,7,8),both.finalSemesters());
 }
 @Test void rejectsUnsupportedOrContradictorySelections(){
  catalog(36);
  assertThrows(IllegalArgumentException.class,()->service.validate(List.of(1L),List.of(),false,List.of(100),List.of()));
  assertThrows(IllegalArgumentException.class,()->service.validate(List.of(1L),List.of(1),false,List.of(5),List.of()));
  assertThrows(IllegalArgumentException.class,()->service.validate(List.of(1L),List.of(),false,List.of(),List.of(2023)));
  assertDoesNotThrow(()->service.validate(List.of(1L),List.of(1,3),false,List.of(1,5),List.of(2026,2024)));
 }
 @Test void missingDurationDoesNotInventSemesterChoices(){
  catalog((Integer)null);var options=service.read(List.of(1L));assertTrue(options.semesters().isEmpty());assertEquals(1,options.unconfiguredCourses());
  assertThrows(IllegalArgumentException.class,()->service.validate(List.of(1L),List.of(),true,List.of(),List.of()));
 }
}
