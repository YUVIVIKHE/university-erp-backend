package com.university.erp.repository.finance;
import com.university.erp.entity.finance.FeeStructure;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface FeeStructureRepository extends JpaRepository<FeeStructure,Long>{
    @EntityGraph(attributePaths="course")
    @Query("select s from FeeStructure s where (:search='' or lower(s.name) like lower(concat('%',:search,'%')) or lower(s.course.name) like lower(concat('%',:search,'%'))) and (:status='' or s.status=:status)")
    Page<FeeStructure> search(String search,String status,Pageable page);
    @EntityGraph(attributePaths={"course","lines"})
    @Query("select s from FeeStructure s where s.id=:id") Optional<FeeStructure> detail(Long id);
}
