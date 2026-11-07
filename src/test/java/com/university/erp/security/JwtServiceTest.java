package com.university.erp.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService service = new JwtService("test-only-signing-key-at-least-32-bytes-long", 60_000, 120_000);

    @Test void tokenTypesAreDistinct() {
        var user = User.withUsername("accountant@example.com").password("unused").roles("ACCOUNTANT").build();
        String access = service.generateAccessToken(user);
        String refresh = service.generateRefreshToken(user);
        assertTrue(service.hasType(access, "access"));
        assertFalse(service.hasType(access, "refresh"));
        assertTrue(service.hasType(refresh, "refresh"));
        assertFalse(service.hasType(refresh, "access"));
        assertTrue(service.isValid(access, user));
    }

    @Test void disabledAccountsAreRejected() {
        var user = User.withUsername("accountant@example.com").password("unused").roles("ACCOUNTANT").disabled(true).build();
        assertFalse(service.isValid(service.generateAccessToken(user), user));
    }

    @Test void accessChangesRevokeAccessAndRefreshTokensPermanently() {
        var account = com.university.erp.entity.User.builder().email("staff@example.com")
            .role(com.university.erp.entity.User.Role.ACCOUNTANT).isActive(true).build();
        String access = service.generateAccessToken(account);
        String refresh = service.generateRefreshToken(account);
        assertTrue(service.isValid(access, account));
        account.setTokenVersion(1);
        assertFalse(service.isValid(access, account));
        assertFalse(service.isValid(refresh, account));
        account.setActive(false); account.setActive(true);
        assertFalse(service.isValid(access, account));
        account.setInvitationPending(true);
        assertFalse(service.isValid(service.generateAccessToken(account), account));
    }
}
