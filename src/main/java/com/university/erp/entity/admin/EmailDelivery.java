package com.university.erp.entity.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Table(name = "email_deliveries") @Getter @Setter
public class EmailDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String recipient;
    @Column(nullable = false) private String purpose;
    @Column(nullable = false) private boolean sent;
    @Column(nullable = false) private String message;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
