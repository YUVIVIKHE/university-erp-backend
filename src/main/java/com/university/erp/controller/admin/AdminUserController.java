package com.university.erp.controller.admin;
import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.User;
import com.university.erp.service.admin.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/users") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService users;
    private final InvitationService invitations;
    @GetMapping public Page<AdminUserService.UserView> list(@RequestParam(defaultValue="") String search,
        @RequestParam(required=false) User.Role role, @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="0") int page) {
        return users.list(search, role, status, page);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AdminUserService.Created create(@Valid @RequestBody AdminRequests.CreateUser request, @AuthenticationPrincipal User actor) {
        return users.create(request, actor);
    }
    @PatchMapping("/{id}") public AdminUserService.UserView update(@PathVariable Long id,
        @Valid @RequestBody AdminRequests.UpdateUser request, @AuthenticationPrincipal User actor) { return users.update(id, request, actor); }
    @PostMapping("/{id}/invitation") public EmailService.DeliveryResult invite(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return invitations.issue(id, actor);
    }
}
