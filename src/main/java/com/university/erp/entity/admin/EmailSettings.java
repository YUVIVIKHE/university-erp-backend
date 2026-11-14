package com.university.erp.entity.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Table(name = "email_settings") @Getter @Setter
public class EmailSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String host;
    @Column(nullable = false) private int port;
    @Column(nullable = false) private String username;
    @Column(name = "password_encrypted", nullable = false, columnDefinition = "TEXT") private String passwordEncrypted;
    @Column(nullable = false) private String senderName;
    @Column(nullable = false) private String senderEmail;
    private String replyTo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Security security;
    @Column(nullable = false) private String publicUrl;
    @Column(nullable = false) private boolean enabled;
    private OffsetDateTime updatedAt;
    @Version private long version;
    @PrePersist @PreUpdate void touch() { updatedAt = OffsetDateTime.now(); }
    public enum Security { STARTTLS, SSL }
}
