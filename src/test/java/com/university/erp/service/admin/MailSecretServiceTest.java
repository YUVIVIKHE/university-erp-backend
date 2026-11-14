package com.university.erp.service.admin;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class MailSecretServiceTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    @Test void encryptsWithRandomNonceAndDecrypts() {
        var service = new MailSecretService(key);
        String first = service.encrypt("mail-password");
        assertNotEquals("mail-password", first);
        assertNotEquals(first, service.encrypt("mail-password"));
        assertEquals("mail-password", service.decrypt(first));
    }
    @Test void missingKeyIsRejected() {
        var service = new MailSecretService("");
        assertFalse(service.configured());
        assertThrows(IllegalStateException.class, () -> service.encrypt("password"));
    }
    @Test void tamperingOrChangingKeyIsRejected() {
        var service = new MailSecretService(key);
        String encrypted = service.encrypt("password");
        byte[] changedKey = new byte[32]; changedKey[0] = 1;
        assertThrows(IllegalStateException.class, () -> new MailSecretService(Base64.getEncoder().encodeToString(changedKey)).decrypt(encrypted));
        byte[] changed = Base64.getDecoder().decode(encrypted); changed[changed.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> service.decrypt(Base64.getEncoder().encodeToString(changed)));
    }
}
