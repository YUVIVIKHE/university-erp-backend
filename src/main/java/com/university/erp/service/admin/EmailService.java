package com.university.erp.service.admin;

import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.User;
import com.university.erp.entity.admin.*;
import com.university.erp.repository.admin.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.OffsetDateTime;

@Service @RequiredArgsConstructor
public class EmailService {
    private final EmailSettingsRepository settings;
    private final EmailDeliveryRepository deliveries;
    private final MailSecretService secrets;
    private final EmailTransport transport;
    private final AdminAuditService audit;

    public record SettingsView(Long id, boolean configured, boolean encryptionReady, String host, int port,
        String username, boolean passwordConfigured, String senderName, String senderEmail,
        String replyTo, EmailSettings.Security security, String publicUrl, boolean enabled, OffsetDateTime updatedAt) {}
    public record DeliveryResult(boolean sent, String message) {}

    public record ProfilesView(boolean encryptionReady, java.util.List<SettingsView> accounts) {}

    private SettingsView view(EmailSettings config) {
        return new SettingsView(config.getId(), true, secrets.configured(), config.getHost(), config.getPort(), config.getUsername(),
            config.getPasswordEncrypted() != null, config.getSenderName(), config.getSenderEmail(), config.getReplyTo(),
            config.getSecurity(), config.getPublicUrl(), config.isEnabled(), config.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public ProfilesView profiles() {
        return new ProfilesView(secrets.configured(), settings.findAllByOrderByIdDesc().stream().map(this::view).toList());
    }

    @Transactional(readOnly = true)
    public SettingsView read() {
        var config = settings.findFirstByEnabledTrueOrderByIdDesc().orElseGet(() -> settings.findFirstByOrderByIdDesc().orElse(null));
        return config == null
            ? new SettingsView(null, false, secrets.configured(), "", 587, "", false, "", "", "", EmailSettings.Security.STARTTLS, "", false, null)
            : view(config);
    }

    @Transactional
    public SettingsView save(AdminRequests.Smtp req, User actor) {
        var current = settings.findFirstByEnabledTrueOrderByIdDesc().orElseGet(() -> settings.findFirstByOrderByIdDesc().orElse(null));
        return saveProfile(current == null ? null : current.getId(), req, actor);
    }

    @Transactional
    public SettingsView saveProfile(Long id, AdminRequests.Smtp req, User actor) {
        var profiles = settings.lockProfiles();
        String host = req.host().trim();
        if (!host.matches("[a-zA-Z0-9.-]+") || host.contains(".."))
            throw new IllegalArgumentException("Enter a valid SMTP hostname without a protocol or path.");
        String publicUrl = validateUrl(req.publicUrl());
        rejectNewlines(req.username(), req.senderName(), req.senderEmail(), req.replyTo());
        var config = id == null ? new EmailSettings() : profiles.stream().filter(p -> p.getId().equals(id)).findFirst()
            .orElseThrow(() -> new java.util.NoSuchElementException("SMTP account not found"));
        boolean accountChanged = !host.equals(config.getHost()) || !req.username().trim().equals(config.getUsername());
        if (req.password() != null && !req.password().isEmpty()) config.setPasswordEncrypted(secrets.encrypt(req.password()));
        else if (config.getPasswordEncrypted() == null || accountChanged)
            throw new IllegalArgumentException("Enter the SMTP password for this server and username.");
        if (!secrets.configured()) throw new IllegalStateException("Configure the server email encryption key first.");
        config.setHost(host); config.setPort(req.port()); config.setUsername(req.username().trim());
        config.setSenderName(req.senderName().trim()); config.setSenderEmail(req.senderEmail().trim());
        config.setReplyTo(req.replyTo() == null || req.replyTo().isBlank() ? null : req.replyTo().trim());
        config.setSecurity(req.security()); config.setPublicUrl(publicUrl);
        if (req.enabled()) {
            profiles.stream().filter(p -> p != config && p.isEnabled()).forEach(p -> p.setEnabled(false));
            settings.flush();
        }
        config.setEnabled(req.enabled());
        settings.saveAndFlush(config);
        audit.record(actor, "EMAIL_SETTINGS_UPDATED", "EmailSettings", config.getId());
        return view(config);
    }

    @Transactional
    public SettingsView setActive(Long id, boolean active, User actor) {
        var profiles = settings.lockProfiles();
        var config = profiles.stream().filter(p -> p.getId().equals(id)).findFirst()
            .orElseThrow(() -> new java.util.NoSuchElementException("SMTP account not found"));
        if (active) {
            // Ensure the encrypted credential is usable before switching senders.
            secrets.decrypt(config.getPasswordEncrypted());
            profiles.stream().filter(p -> p != config && p.isEnabled()).forEach(p -> p.setEnabled(false));
            settings.flush();
        }
        config.setEnabled(active); settings.flush();
        audit.record(actor, active ? "EMAIL_ACCOUNT_ACTIVATED" : "EMAIL_ACCOUNT_DEACTIVATED", "EmailSettings", id);
        return view(config);
    }

    static String validateUrl(String value) {
        try {
            URI url = URI.create(value.trim());
            boolean local = "localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost());
            if (url.getHost() == null || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null
                || !("https".equals(url.getScheme()) || (local && "http".equals(url.getScheme())))
                || (url.getPath() != null && !url.getPath().isEmpty() && !url.getPath().equals("/")))
                throw new IllegalArgumentException();
            return value.trim().replaceAll("/+$", "");
        } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Use the HTTPS portal origin, or http://localhost:3000 for local development."); }
    }

    private void rejectNewlines(String... values) {
        for (String value : values) if (value != null && (value.contains("\n") || value.contains("\r")))
            throw new IllegalArgumentException("Email settings cannot contain line breaks.");
    }

    public String publicUrl() { return settings.findFirstByEnabledTrueOrderByIdDesc().map(EmailSettings::getPublicUrl).orElse(null); }

    @Transactional
    public DeliveryResult sendInvitation(User user, String token) {
        var config = settings.findFirstByEnabledTrueOrderByIdDesc().orElse(null);
        String body = config == null ? "" : "Hello " + user.getFullName() + ",\n\nYour university account is ready to set up.\nLogin ID: "
            + user.getEmail() + "\nRole: " + user.getRole().name()
            + "\n\nSet your password using this single-use link (expires in 24 hours):\n"
            + config.getPublicUrl() + "/accept-invitation#token=" + token
            + "\n\nA newer invitation replaces this link. If you did not expect this invitation, contact your university administrator.";
        return sendUsing(config, user.getEmail(), "USER_INVITATION", "Set up your university account", body);
    }

    @Transactional
    public DeliveryResult send(String recipient, String purpose, String subject, String body) {
        return sendUsing(settings.findFirstByEnabledTrueOrderByIdDesc().orElse(null), recipient, purpose, subject, body);
    }

    private DeliveryResult sendUsing(EmailSettings config, String recipient, String purpose, String subject, String body) {
        boolean sent = false;
        String message;
        if (config == null || !config.isEnabled()) message = "University email is not enabled. Configure SMTP settings, then resend.";
        else {
            try {
                transport.send(config, secrets.decrypt(config.getPasswordEncrypted()), recipient, subject, body);
                sent = true; message = "Accepted by the SMTP server. Check the recipient inbox or spam folder.";
            } catch (Exception ex) { message = failureMessage(ex); }
        }
        var delivery = new EmailDelivery();
        delivery.setRecipient(recipient); delivery.setPurpose(purpose); delivery.setSent(sent); delivery.setMessage(message);
        deliveries.save(delivery);
        return new DeliveryResult(sent, message);
    }

    static String failureMessage(Exception error) {
        var pending = new java.util.ArrayDeque<Throwable>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        pending.add(error);
        boolean authentication = false, timeout = false, network = false, tls = false, key = false;
        while (!pending.isEmpty()) {
            var cause = pending.removeFirst();
            if (!seen.add(cause)) continue;
            authentication |= cause instanceof org.springframework.mail.MailAuthenticationException || cause instanceof jakarta.mail.AuthenticationFailedException;
            timeout |= cause instanceof java.net.SocketTimeoutException;
            network |= cause instanceof java.net.SocketException || cause instanceof java.net.UnknownHostException || cause instanceof SecurityException;
            tls |= cause instanceof javax.net.ssl.SSLException;
            key |= cause instanceof IllegalStateException;
            if (cause.getCause() != null) pending.add(cause.getCause());
            if (cause instanceof jakarta.mail.MessagingException mail && mail.getNextException() != null) pending.add(mail.getNextException());
            if (cause instanceof org.springframework.mail.MailSendException mail)
                java.util.Collections.addAll(pending, mail.getMessageExceptions());
        }
        if (authentication) return "SMTP login was rejected. Check the mailbox username and app password, then save and retry.";
        if (tls) return "A secure connection to the mail server failed. Check the port, security mode and server certificate.";
        if (timeout) return "The SMTP connection timed out. Check the server network/firewall and SMTP port, then retry.";
        if (network) return "The SMTP server could not be reached. Check its hostname, port and the server network permissions.";
        if (key) return "Saved SMTP credentials could not be read. Check the server encryption key or enter the password again.";
        return "Email could not be sent. Check SMTP credentials, sender permissions and connection settings, then retry.";
    }

    @Transactional
    public DeliveryResult test(String recipient, User actor) {
        var config = settings.findFirstByEnabledTrueOrderByIdDesc().orElse(null);
        var result = sendUsing(config, recipient, "SMTP_TEST", "University ERP email test", "This is a test email from your university ERP. No account changes were made.");
        audit.record(actor, "EMAIL_TEST_REQUESTED", "EmailSettings", config == null ? null : config.getId());
        return result;
    }
}
