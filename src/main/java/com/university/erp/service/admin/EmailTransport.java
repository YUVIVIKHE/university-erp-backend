package com.university.erp.service.admin;

import com.university.erp.entity.admin.EmailSettings;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailTransport {
    public void send(EmailSettings config, String password, String to, String subject, String body) throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost()); sender.setPort(config.getPort());
        sender.setUsername(config.getUsername()); sender.setPassword(password);
        var props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.connectiontimeout", "10000");
        props.setProperty("mail.smtp.timeout", "10000");
        props.setProperty("mail.smtp.writetimeout", "10000");
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.starttls.enable", String.valueOf(config.getSecurity() == EmailSettings.Security.STARTTLS));
        props.setProperty("mail.smtp.starttls.required", String.valueOf(config.getSecurity() == EmailSettings.Security.STARTTLS));
        props.setProperty("mail.smtp.ssl.enable", String.valueOf(config.getSecurity() == EmailSettings.Security.SSL));
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(config.getSenderEmail(), config.getSenderName());
        if (config.getReplyTo() != null && !config.getReplyTo().isBlank()) helper.setReplyTo(config.getReplyTo());
        helper.setTo(to); helper.setSubject(subject); helper.setText(body, false);
        sender.send(message);
    }
}
