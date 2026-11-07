package com.university.erp.service.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MailSecretService {
    private final String key;
    public MailSecretService(@Value("${app.mail.encryption-key:}") String key) { this.key = key; }
    private SecretKeySpec key() {
        try {
            byte[] decoded = Base64.getDecoder().decode(key);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Email encryption key must be configured on the server before saving SMTP credentials.");
        }
    }
    public String encrypt(String secret) {
        var encryptionKey = key();
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception ex) { throw new IllegalStateException("Unable to protect email credentials."); }
    }
    public String decrypt(String value) {
        var encryptionKey = key();
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to read email credentials. Check the server encryption key."); }
    }
    public boolean configured() { try { key(); return true; } catch (IllegalStateException ex) { return false; } }
}
