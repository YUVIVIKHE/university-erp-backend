package com.university.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "timetables",
    uniqueConstraints = @UniqueConstraint(columnNames = {"program","course","batch_year","semester"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String program;

    @Column(nullable = false, length = 255)
    private String course;

    @Column(name = "batch_year", nullable = false)
    private Short batchYear;

    @Column(nullable = false)
    private Short semester;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @OneToMany(mappedBy = "timetable", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("day ASC, slotOrder ASC, startTime ASC")
    @Builder.Default
    private List<TimetableSlot> slots = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }

    public enum Status { DRAFT, PUBLISHED }
}
