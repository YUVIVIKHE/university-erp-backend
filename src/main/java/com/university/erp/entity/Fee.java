package com.university.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Fee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fee_id", nullable = false, unique = true, length = 30)
    private String feeId;

    // Link to admission (student name, program come from here)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_id", referencedColumnName = "admission_id", nullable = false)
    private Admission admission;

    @Column(nullable = false, length = 60)
    private String feeType;   // e.g. Tuition, Hostel, Library, Exam

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    @Builder.Default
    @Column(name="concession_amount",nullable=false,precision=12,scale=2)
    private BigDecimal concessionAmount = BigDecimal.ZERO;

    public BigDecimal netAmount() { return amount.subtract(concessionAmount == null ? BigDecimal.ZERO : concessionAmount); }

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private String remarks;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
        if (amountPaid == null) amountPaid = BigDecimal.ZERO;
        if (status == null) status = Status.PENDING;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public enum Status {
        PENDING, PARTIAL, PAID, OVERDUE, WAIVED
    }
}
