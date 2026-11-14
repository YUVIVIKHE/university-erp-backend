package com.university.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "timetable_slots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimetableSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning timetable */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek day;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SlotType type;

    /** null when type = BREAK or FREE */
    @Column(name = "subject_code", length = 50)
    private String subjectCode;

    @Column(name = "subject_name", length = 255)
    private String subjectName;

    @Column(name = "faculty_name", length = 255)
    private String facultyName;

    @Column(name = "room", length = 50)
    private String room;

    @Column(name = "slot_order")
    private Integer slotOrder;

    public enum DayOfWeek { MON, TUE, WED, THU, FRI, SAT }
    public enum SlotType  { CLASS, BREAK, FREE, LAB }
}
