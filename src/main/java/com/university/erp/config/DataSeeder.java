package com.university.erp.config;

import com.university.erp.entity.Permission;
import com.university.erp.entity.Student;
import com.university.erp.entity.User;
import com.university.erp.repository.PermissionRepository;
import com.university.erp.repository.StudentRepository;
import com.university.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository       userRepo;
    private final StudentRepository    studentRepo;
    private final PermissionRepository permRepo;
    private final PasswordEncoder      encoder;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${app.bootstrap.student-email:}")
    private String studentEmail;

    @Value("${app.bootstrap.student-password:}")
    private String studentPassword;

    @Value("${app.bootstrap.student-reset-password:false}")
    private boolean resetStudentPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedAdmin();
        seedStudent();
    }

    private void seedPermissions() {
        List<String[]> perms = List.of(
            new String[]{"VIEW_DASHBOARD",    "Access the main dashboard"},
            new String[]{"MANAGE_USERS",      "Create, update, and deactivate users"},
            new String[]{"MANAGE_STUDENTS",   "Manage student records"},
            new String[]{"VIEW_GRADES",       "View grade records"},
            new String[]{"MANAGE_GRADES",     "Create and update grades"},
            new String[]{"VIEW_ATTENDANCE",   "View attendance records"},
            new String[]{"MANAGE_ATTENDANCE", "Mark and update attendance"},
            new String[]{"VIEW_FEES",         "View fee records"},
            new String[]{"MANAGE_FEES",       "Create and update fee records"},
            new String[]{"VIEW_AUDIT_LOGS",   "View system audit logs"}
        );
        for (String[] p : perms) {
            if (permRepo.findByName(p[0]).isEmpty()) {
                permRepo.save(Permission.builder().name(p[0]).description(p[1]).build());
            }
        }
    }

    private void seedAdmin() {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) return;
        String email = adminEmail.trim();
        if (userRepo.existsByEmail(email)) return;

        User admin = User.builder()
            .email(email)
            .passwordHash(encoder.encode(adminPassword))
            .fullName("System Administrator")
            .role(User.Role.ADMIN)
            .isActive(true)
            .build();

        userRepo.save(admin);
        log.info("Bootstrap administrator created");
    }

    private void seedStudent() {
        if (studentEmail == null || studentEmail.isBlank() || studentPassword == null || studentPassword.isBlank()) return;
        String email = studentEmail.trim();
        var existingUser = userRepo.findByEmailIgnoreCase(email);
        var user = existingUser.orElseGet(() -> userRepo.save(User.builder()
            .email(email)
            .passwordHash(encoder.encode(studentPassword))
            .fullName("Fair Bazaar Student")
            .role(User.Role.STUDENT)
            .isActive(true)
            .build()));

        boolean userChanged = false;
        if (user.getRole() != User.Role.STUDENT) {
            user.setRole(User.Role.STUDENT);
            userChanged = true;
        }
        if (!user.isActive()) {
            user.setActive(true);
            userChanged = true;
        }
        if (user.isInvitationPending()) {
            user.setInvitationPending(false);
            userChanged = true;
        }
        boolean passwordReset = existingUser.isPresent() && resetStudentPassword;
        if (passwordReset) {
            user.setEmail(email);
            user.setPasswordHash(encoder.encode(studentPassword));
            user.setTokenVersion(user.getTokenVersion() + 1);
            userChanged = true;
        }
        if (userChanged) {
            userRepo.save(user);
        }
        if (passwordReset) {
            log.info("Configured student password reset applied");
        }

        if (studentRepo.findByUserId(user.getId()).isPresent()) return;

        Student student = Student.builder()
            .user(user)
            .rollNumber("FB-2025-001")
            .program("BBA")
            .semester((short) 1)
            .batchYear((short) 2025)
            .dateOfBirth(LocalDate.of(2005, 6, 14))
            .phone("+91 90000 00000")
            .address("Fair Bazaar Campus")
            .guardianName("Guardian")
            .guardianPhone("+91 90000 00001")
            .enrollmentDate(LocalDate.now())
            .isActive(true)
            .build();

        studentRepo.save(student);
        log.info("Bootstrap student account created");
    }
}
