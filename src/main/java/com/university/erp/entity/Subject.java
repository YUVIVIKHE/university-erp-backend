package com.university.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "subjects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_code", nullable = false, unique = true, length = 30)
    private String subjectCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    /** matches Course.program (e.g. "B.Tech CSE") */
    @Column(nullable = false, length = 100)
    private String course;

    /** 1-12 */
    @Column(nullable = false)
    private Integer semester;

    /** calendar year, e.g. 2024 */
    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer credits;

    /** THEORY, PRACTICAL, TUTORIAL */
    @Column(nullable = false, length = 20)
    private String type;

    /** name of the faculty member */
    @Column(name = "faculty_name", length = 120)
    private String facultyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public enum Status { ACTIVE, INACTIVE }
}
