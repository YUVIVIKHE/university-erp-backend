package com.university.erp.controller;
import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.service.admin.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth/invitations") @RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitations;
    @PostMapping("/inspect") public InvitationService.InvitationView inspect(@Valid @RequestBody AdminRequests.Token request) {
        return invitations.inspect(request.token());
    }
    @PostMapping("/accept") public InvitationService.Accepted accept(@Valid @RequestBody AdminRequests.AcceptInvitation request) {
        return invitations.accept(request);
    }
}
