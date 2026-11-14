package com.university.erp.config;

import com.university.erp.entity.User;
import com.university.erp.entity.Student;
import com.university.erp.repository.PermissionRepository;
import com.university.erp.repository.StudentRepository;
import com.university.erp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataSeederTest {
    private final UserRepository users = mock(UserRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final DataSeeder seeder = new DataSeeder(users, students, mock(PermissionRepository.class), encoder);

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

    @Test void explicitlyConfiguredStudentCreatesAProfile() {
        ReflectionTestUtils.setField(seeder, "studentEmail", "student@example.com");
        ReflectionTestUtils.setField(seeder, "studentPassword", "fixture-only-password");
        when(encoder.encode("fixture-only-password")).thenReturn("hashed-fixture");
        when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(students.findByUserId(1L)).thenReturn(Optional.empty());

        seeder.run(null);

        var savedUser = ArgumentCaptor.forClass(User.class);
        var savedStudent = ArgumentCaptor.forClass(Student.class);
        verify(users).save(savedUser.capture());
        verify(students).save(savedStudent.capture());
        assertEquals(User.Role.STUDENT, savedUser.getValue().getRole());
        assertEquals("hashed-fixture", savedUser.getValue().getPasswordHash());
        assertEquals(savedUser.getValue(), savedStudent.getValue().getUser());
    }

    @Test void existingStudentPasswordIsResetOnlyWhenExplicitlyEnabled() {
        ReflectionTestUtils.setField(seeder, "studentEmail", "student@example.com");
        ReflectionTestUtils.setField(seeder, "studentPassword", "fixture-only-password");
        ReflectionTestUtils.setField(seeder, "resetStudentPassword", true);
        User student = User.builder()
            .id(1L)
            .email("Student@example.com")
            .fullName("Student")
            .role(User.Role.STUDENT)
            .isActive(true)
            .tokenVersion(4)
            .build();
        when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(student));
        when(students.findByUserId(1L)).thenReturn(Optional.of(Student.builder().build()));
        when(encoder.encode("fixture-only-password")).thenReturn("hashed-fixture");

        seeder.run(null);

        verify(users).save(student);
        assertEquals("student@example.com", student.getEmail());
        assertEquals("hashed-fixture", student.getPasswordHash());
        assertEquals(5, student.getTokenVersion());
    }

    @Test void existingStudentPasswordIsNotChangedByDefault() {
        ReflectionTestUtils.setField(seeder, "studentEmail", "student@example.com");
        ReflectionTestUtils.setField(seeder, "studentPassword", "fixture-only-password");
        User student = User.builder()
            .id(1L)
            .email("student@example.com")
            .fullName("Student")
            .role(User.Role.STUDENT)
            .isActive(true)
            .build();
        when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(student));
        when(students.findByUserId(1L)).thenReturn(Optional.of(Student.builder().build()));

        seeder.run(null);

        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }
}
