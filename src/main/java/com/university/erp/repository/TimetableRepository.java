package com.university.erp.repository;

import com.university.erp.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    @Query("SELECT t FROM Timetable t LEFT JOIN FETCH t.slots WHERE t.program = :program ORDER BY t.batchYear DESC, t.semester ASC")
    List<Timetable> findByProgramOrderByBatchYearDescSemesterAsc(@Param("program") String program);

    @Query("SELECT t FROM Timetable t LEFT JOIN FETCH t.slots WHERE t.program = :program AND t.course = :course AND t.batchYear = :batchYear AND t.semester = :semester")
    Optional<Timetable> findByProgramAndCourseAndBatchYearAndSemester(
        @Param("program") String program,
        @Param("course") String course,
        @Param("batchYear") Short batchYear,
        @Param("semester") Short semester);
}
