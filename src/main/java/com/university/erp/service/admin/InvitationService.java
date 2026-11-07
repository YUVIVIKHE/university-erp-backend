package com.university.erp.service.admin;

import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.User;
import com.university.erp.entity.admin.UserInvitation;
import com.university.erp.repository.UserRepository;
import com.university.erp.repository.admin.UserInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class InvitationService {
    private final UserRepository users;
    private final UserInvitationRepository invitations;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final AdminAuditService audit;

    public record InvitationView(String email, String fullName, User.Role role, OffsetDateTime expiresAt) {}
    public record Accepted(String message, String loginPath) {}

    @Transactional
    public EmailService.DeliveryResult issue(Long userId, User actor) {
        var user = users.lockById(userId).orElseThrow(() -> new NoSuchElementException("User not found"));
        if (user.getRole() == User.Role.ADMIN || user.getId().equals(actor.getId()))
            throw new IllegalArgumentException("Admin account invitations are managed separately.");
        if (!user.isActive()) throw new IllegalArgumentException("Activate this account before sending an invitation.");
        var invitation = invitations.findByUserId(userId).orElseGet(UserInvitation::new);
        if (invitation.getCreatedAt() != null && invitation.getCreatedAt().isAfter(OffsetDateTime.now().minusSeconds(30)))
            throw new IllegalArgumentException("Please wait 30 seconds before resending an invitation.");
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        user.setInvitationPending(true); user.setTokenVersion(user.getTokenVersion() + 1);
        invitation.setUser(user); invitation.setTokenHash(hash(token));
        invitation.setCreatedAt(OffsetDateTime.now()); invitation.setExpiresAt(OffsetDateTime.now().plusHours(24));
        invitation.setUsedAt(null); invitation.setRevokedAt(null);
        invitation.setDelivery(UserInvitation.Delivery.PENDING);
        invitations.saveAndFlush(invitation);
        var result = email.sendInvitation(user, token);
        invitation.setDelivery(result.sent() ? UserInvitation.Delivery.SENT : UserInvitation.Delivery.FAILED);
        invitation.setDeliveryMessage(result.message());
        audit.record(actor, "USER_INVITATION_ISSUED", "User", userId);
        return result;
    }

    @Transactional(readOnly = true)
    public InvitationView inspect(String token) {
        var invitation = invitations.findByTokenHash(hash(token)).orElseThrow(this::invalid);
        requireValid(invitation);
        var user = invitation.getUser();
        return new InvitationView(user.getEmail(), user.getFullName(), user.getRole(), invitation.getExpiresAt());
    }

    @Transactional
    public Accepted accept(AdminRequests.AcceptInvitation request) {
        String hash = hash(request.token());
        Long userId = invitations.userIdForToken(hash).orElseThrow(this::invalid);
        var user = users.lockById(userId).orElseThrow(this::invalid);
        var invitation = invitations.lockByTokenHash(hash).orElseThrow(this::invalid);
        requireValid(invitation);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes.");
        user.setPasswordHash(encoder.encode(request.password()));
        user.setInvitationPending(false); user.setTokenVersion(user.getTokenVersion() + 1);
        invitation.setUsedAt(OffsetDateTime.now());
        audit.record(user, "INVITATION_ACCEPTED", "User", user.getId());
        return new Accepted("Your password has been set. You can now sign in.", user.getRole() == User.Role.ACCOUNTANT ? "/accountant/login" : "/login");
    }

    private void requireValid(UserInvitation invitation) {
        if (invitation.getUsedAt() != null || invitation.getRevokedAt() != null
            || !invitation.getExpiresAt().isAfter(OffsetDateTime.now())
            || !invitation.getUser().isActive() || !invitation.getUser().isInvitationPending()) throw invalid();
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("This invitation is invalid, expired or already used. Ask your administrator for a new invitation."); }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
