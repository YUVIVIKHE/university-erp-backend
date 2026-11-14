package com.university.erp.service.admin;

import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.User;
import com.university.erp.entity.admin.EmailSettings;
import com.university.erp.repository.admin.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    private final EmailSettingsRepository settings = mock(EmailSettingsRepository.class);
    private final EmailDeliveryRepository deliveries = mock(EmailDeliveryRepository.class);
    private final MailSecretService secrets = mock(MailSecretService.class);
    private final EmailTransport transport = mock(EmailTransport.class);
    private final EmailService service = new EmailService(settings, deliveries, secrets, transport, mock(AdminAuditService.class));

    @Test void absentSmtpRecordsFailureWithoutSending() {
        when(settings.findFirstByEnabledTrueOrderByIdDesc()).thenReturn(Optional.empty());
        assertFalse(service.send("recipient@example.com", "INVITE", "Subject", "Body").sent());
        verifyNoInteractions(transport); verify(deliveries).save(any());
    }
    @Test void deliveryUsesConfiguredSenderAndNeverReturnsProviderException() throws Exception {
        var config = new EmailSettings(); config.setEnabled(true); config.setPasswordEncrypted("encrypted"); config.setSenderEmail("office@example.com");
        when(settings.findFirstByEnabledTrueOrderByIdDesc()).thenReturn(Optional.of(config)); when(secrets.decrypt("encrypted")).thenReturn("private-password");
        doThrow(new RuntimeException("private-password")).when(transport).send(any(), anyString(), anyString(), anyString(), anyString());
        var result = service.send("recipient@example.com", "TEST", "Subject", "Body");
        assertFalse(result.sent()); assertFalse(result.message().contains("private-password"));
        verify(transport).send(eq(config), eq("private-password"), eq("recipient@example.com"), eq("Subject"), eq("Body"));
    }
    @Test void blankPasswordPreservesEncryptedCredentialAndIsNotInView() {
        var config = new EmailSettings(); config.setId(1L); config.setHost("smtp.example.com"); config.setUsername("office@example.com"); config.setPasswordEncrypted("encrypted");
        when(settings.findFirstByEnabledTrueOrderByIdDesc()).thenReturn(Optional.of(config));
        when(settings.lockProfiles()).thenReturn(java.util.List.of(config)); when(secrets.configured()).thenReturn(true);
        var req = new AdminRequests.Smtp("smtp.example.com", 587, "office@example.com", "", "Office", "office@example.com", "", EmailSettings.Security.STARTTLS, "https://erp.example.com", true);
        var view = service.save(req, new User());
        assertEquals("encrypted", config.getPasswordEncrypted()); assertTrue(view.passwordConfigured());
        assertFalse(view.toString().contains("encrypted")); verify(secrets, never()).encrypt(any());
    }
    @Test void invitationOriginRejectsUntrustedSchemesAndUserInfo() {
        assertEquals("https://erp.example.com", EmailService.validateUrl("https://erp.example.com/"));
        assertThrows(IllegalArgumentException.class, () -> EmailService.validateUrl("http://erp.example.com"));
        assertThrows(IllegalArgumentException.class, () -> EmailService.validateUrl("https://attacker@erp.example.com"));
    }

    @Test void safeErrorsDistinguishNetworkAndAuthenticationFailures() {
        var timeout = new jakarta.mail.MessagingException("sensitive provider response", new java.net.SocketTimeoutException());
        var wrapped = new org.springframework.mail.MailSendException("private-password", timeout);
        assertTrue(EmailService.failureMessage(wrapped).contains("timed out"));
        assertFalse(EmailService.failureMessage(wrapped).contains("private-password"));
        assertTrue(EmailService.failureMessage(new org.springframework.mail.MailAuthenticationException("secret")).contains("login was rejected"));
        assertTrue(EmailService.failureMessage(new java.net.SocketException("Permission denied")).contains("could not be reached"));
    }

    @Test void activationDeactivatesOldAccountAndAllowsAllInactive() {
        var first = new EmailSettings(); first.setId(1L); first.setEnabled(true);
        var second = new EmailSettings(); second.setId(2L); second.setPasswordEncrypted("encrypted");
        when(settings.lockProfiles()).thenReturn(java.util.List.of(first, second));
        when(secrets.decrypt("encrypted")).thenReturn("password");
        service.setActive(2L, true, new User());
        assertFalse(first.isEnabled()); assertTrue(second.isEnabled());
        service.setActive(2L, false, new User());
        assertFalse(first.isEnabled()); assertFalse(second.isEnabled());
    }

    @Test void addingActiveAccountPreservesOldAccountAsInactive() {
        var previous = new EmailSettings(); previous.setId(1L); previous.setEnabled(true);
        when(settings.lockProfiles()).thenReturn(java.util.List.of(previous));
        when(secrets.configured()).thenReturn(true); when(secrets.encrypt("new-password")).thenReturn("encrypted-new");
        var request = new AdminRequests.Smtp("smtp.example.com", 465, "new@example.com", "new-password", "Office", "new@example.com", "", EmailSettings.Security.SSL, "https://erp.example.com", true);
        var result = service.saveProfile(null, request, new User());
        assertFalse(previous.isEnabled()); assertTrue(result.enabled());
        assertEquals("new@example.com", result.username());
        verify(settings, never()).delete(any());
    }

    @Test void invitationUsesSameProfileForSenderAndLink() throws Exception {
        var config = new EmailSettings(); config.setId(2L); config.setEnabled(true); config.setPublicUrl("https://college.example.com"); config.setPasswordEncrypted("encrypted");
        when(settings.findFirstByEnabledTrueOrderByIdDesc()).thenReturn(Optional.of(config));
        when(secrets.decrypt("encrypted")).thenReturn("password");
        var user = User.builder().email("user@example.com").fullName("User").role(User.Role.ACCOUNTANT).build();
        service.sendInvitation(user, "test-token");
        verify(settings, times(1)).findFirstByEnabledTrueOrderByIdDesc();
        verify(transport).send(eq(config), eq("password"), eq(user.getEmail()), anyString(), contains("https://college.example.com/accept-invitation#token=test-token"));
    }
}
