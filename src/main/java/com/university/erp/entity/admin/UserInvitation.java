package com.university.erp.entity.admin;

import com.university.erp.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Table(name = "user_invitations") @Getter @Setter
public class UserInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, unique = true) private User user;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private OffsetDateTime expiresAt;
    @Column(nullable = false) private OffsetDateTime createdAt;
    private OffsetDateTime usedAt;
    private OffsetDateTime revokedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Delivery delivery = Delivery.PENDING;
    private String deliveryMessage;
    public enum Delivery { PENDING, SENT, FAILED }
}
