package com.university.erp.config;

import com.university.erp.entity.User;
import com.university.erp.repository.PermissionRepository;
import com.university.erp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataSeederTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final DataSeeder seeder = new DataSeeder(users, mock(PermissionRepository.class), encoder);

    @Test void noDefaultAdminIsCreatedWithoutExplicitCredentials() {
        seeder.run(null);
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test void existingAdminIsNeverOverwritten() {
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "fixture-only-password");
        when(users.existsByEmail("admin@example.com")).thenReturn(true);
        seeder.run(null);
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test void explicitlyConfiguredAdminUsesHashedPassword() {
        ReflectionTestUtils.setField(seeder, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "fixture-only-password");
        when(encoder.encode("fixture-only-password")).thenReturn("hashed-fixture");
        seeder.run(null);
        var captured = ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertEquals("hashed-fixture", captured.getValue().getPasswordHash());
        assertEquals(User.Role.ADMIN, captured.getValue().getRole());
    }
}
