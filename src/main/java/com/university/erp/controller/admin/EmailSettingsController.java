package com.university.erp.controller.admin;
import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.User;
import com.university.erp.service.admin.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/email-settings") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class EmailSettingsController {
    private final EmailService email;
    @GetMapping public EmailService.SettingsView read() { return email.read(); }
    @GetMapping("/accounts") public EmailService.ProfilesView profiles() { return email.profiles(); }
    @PostMapping("/accounts") public EmailService.SettingsView create(@Valid @RequestBody AdminRequests.Smtp request, @AuthenticationPrincipal User actor) {
        return email.saveProfile(null, request, actor);
    }
    @PutMapping("/accounts/{id}") public EmailService.SettingsView update(@PathVariable Long id, @Valid @RequestBody AdminRequests.Smtp request, @AuthenticationPrincipal User actor) {
        return email.saveProfile(id, request, actor);
    }
    @PatchMapping("/accounts/{id}/status") public EmailService.SettingsView status(@PathVariable Long id, @Valid @RequestBody AdminRequests.EmailStatus request, @AuthenticationPrincipal User actor) {
        return email.setActive(id, request.active(), actor);
    }
    @PutMapping public EmailService.SettingsView save(@Valid @RequestBody AdminRequests.Smtp request, @AuthenticationPrincipal User actor) {
        return email.save(request, actor);
    }
    @PostMapping("/test") public EmailService.DeliveryResult test(@Valid @RequestBody AdminRequests.TestEmail request, @AuthenticationPrincipal User actor) {
        return email.test(request.recipient(), actor);
    }
}
