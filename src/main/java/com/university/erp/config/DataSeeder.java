package com.university.erp.config;

import com.university.erp.entity.Permission;
import com.university.erp.entity.User;
import com.university.erp.repository.PermissionRepository;
import com.university.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository       userRepo;
    private final PermissionRepository permRepo;
    private final PasswordEncoder      encoder;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedAdmin();
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
}
