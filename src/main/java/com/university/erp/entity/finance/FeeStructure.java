package com.university.erp.entity.finance;

import com.university.erp.entity.Course;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name="finance_fee_structures")
@Getter @Setter @NoArgsConstructor
public class FeeStructure {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @Column(nullable=false,length=120) private String name;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="course_id",nullable=false) private Course course;
    @Column(nullable=false) private int academicYear;
    @Column(nullable=false) private int semester;
    private Integer batchYear;
    private LocalDate effectiveFrom;
    private LocalDate effectiveUntil;
    @Column(length=1000) private String notes;
    @Column(nullable=false,length=20) private String status="DRAFT";
    @Column(nullable=false) private OffsetDateTime createdAt;
    @Column(nullable=false) private OffsetDateTime updatedAt;
    @Column(nullable=false,length=255) private String createdBy;
    @ElementCollection @CollectionTable(name="finance_fee_structure_lines",joinColumns=@JoinColumn(name="structure_id"))
    @OrderColumn(name="line_order") private List<Line> lines=new ArrayList<>();
    @Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Line {
        @Column(nullable=false,length=60) private String feeHead;
        @Column(nullable=false,length=60) private String installment;
        @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
        @Column(nullable=false) private LocalDate dueDate;
    }
    @PrePersist void create(){createdAt=updatedAt=OffsetDateTime.now();}
    @PreUpdate void update(){updatedAt=OffsetDateTime.now();}
}
