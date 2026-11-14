package com.university.erp.service.admin;

import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.User;
import com.university.erp.entity.admin.UserInvitation;
import com.university.erp.repository.UserRepository;
import com.university.erp.repository.admin.UserInvitationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvitationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserInvitationRepository invitations = mock(UserInvitationRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EmailService email = mock(EmailService.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final InvitationService service = new InvitationService(users, invitations, encoder, email, audit);
    private final User user = User.builder().id(1L).email("accountant@example.com").fullName("Accountant")
        .role(User.Role.ACCOUNTANT).isActive(true).invitationPending(true).build();

    private UserInvitation invitation() {
        var result = new UserInvitation(); result.setUser(user);
        result.setTokenHash(InvitationService.hash("test-token"));
        result.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        result.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return result;
    }

    @Test void acceptanceChangesPasswordAndTokenWorksOnlyOnce() {
        var invitation = invitation();
        when(invitations.userIdForToken(invitation.getTokenHash())).thenReturn(Optional.of(1L));
        when(users.lockById(1L)).thenReturn(Optional.of(user));
        when(invitations.lockByTokenHash(invitation.getTokenHash())).thenReturn(Optional.of(invitation));
        when(encoder.encode("a-long-password!")).thenReturn("hashed-password");
        var request = new AdminRequests.AcceptInvitation("test-token", "a-long-password!");
        assertEquals("/accountant/login", service.accept(request).loginPath());
        assertEquals("hashed-password", user.getPasswordHash()); assertFalse(user.isInvitationPending());
        assertNotNull(invitation.getUsedAt()); assertEquals(1, user.getTokenVersion());
        assertThrows(IllegalArgumentException.class, () -> service.accept(request));
        verify(encoder, times(1)).encode(anyString());
    }

    @Test void expiredRevokedAndDisabledInvitationsCannotBeInspected() {
        var invitation = invitation();
        when(invitations.findByTokenHash(invitation.getTokenHash())).thenReturn(Optional.of(invitation));
        invitation.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.inspect("test-token"));
        invitation.setExpiresAt(OffsetDateTime.now().plusHours(1)); invitation.setRevokedAt(OffsetDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> service.inspect("test-token"));
        invitation.setRevokedAt(null); user.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.inspect("test-token"));
    }

    @Test void resendReplacesHashAndRecordsDeliveryFailureWithoutPassword() {
        var invitation = invitation(); String oldHash = invitation.getTokenHash();
        var actor = User.builder().id(99L).role(User.Role.ADMIN).build();
        when(users.lockById(1L)).thenReturn(Optional.of(user));
        when(invitations.findByUserId(1L)).thenReturn(Optional.of(invitation));
        when(email.sendInvitation(eq(user), anyString())).thenReturn(new EmailService.DeliveryResult(false, "Delivery failed"));
        assertFalse(service.issue(1L, actor).sent());
        assertNotEquals(oldHash, invitation.getTokenHash()); assertEquals(64, invitation.getTokenHash().length());
        assertEquals(UserInvitation.Delivery.FAILED, invitation.getDelivery()); assertTrue(user.isInvitationPending());
        verify(email).sendInvitation(eq(user), anyString());
        assertThrows(IllegalArgumentException.class, () -> service.issue(1L, actor));
    }
}
