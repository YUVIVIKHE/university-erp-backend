package com.university.erp.service.admin;

import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.*;
import com.university.erp.exception.ConflictException;
import com.university.erp.repository.*;
import com.university.erp.repository.admin.UserInvitationRepository;
import com.university.erp.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository users;
    private final AdmissionRepository admissions;
    private final StudentService students;
    private final UserInvitationRepository invitations;
    private final InvitationService invitationService;
    private final PasswordEncoder encoder;
    private final AdminAuditService audit;

    public record UserView(Long id, String email, String fullName, User.Role role, boolean active,
        String status, String invitationDelivery, String deliveryMessage, OffsetDateTime invitationExpiresAt) {}
    public record Created(UserView user, EmailService.DeliveryResult delivery) {}

    @Transactional(readOnly = true)
    public Page<UserView> list(String search, User.Role role, String status, int page) {
        if (page < 0 || !Set.of("", "ACTIVE", "PENDING", "INACTIVE").contains(status))
            throw new IllegalArgumentException("Invalid page or account status.");
        return users.searchUsers(search.trim(), role, status, PageRequest.of(page, 20, Sort.by("id").descending())).map(this::view);
    }

    @Transactional
    public Created create(AdminRequests.CreateUser request, User actor) {
        if (request.role() == User.Role.ADMIN) throw new IllegalArgumentException("Create only Student, Faculty or Accountant accounts here.");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("A user with this email already exists. Use its invitation action instead.");
        User user;
        if (request.role() == User.Role.STUDENT) {
            var admission = admissions.findFirstByEmailIgnoreCaseAndStatus(email, Admission.Status.APPROVED)
                .orElseThrow(() -> new IllegalArgumentException("Student accounts require an approved admission with this email. Complete admission approval first."));
            students.provisionFromAdmission(admission);
            user = users.findByEmailIgnoreCase(email).orElseThrow();
        } else {
            user = User.builder().email(email).fullName(request.fullName().trim()).role(request.role())
                .passwordHash(encoder.encode(UUID.randomUUID().toString())).isActive(true).invitationPending(true).build();
            users.saveAndFlush(user);
        }
        audit.record(actor, "USER_CREATED", "User", user.getId());
        var delivery = invitationService.issue(user.getId(), actor);
        return new Created(view(user), delivery);
    }

    @Transactional
    public UserView update(Long id, AdminRequests.UpdateUser request, User actor) {
        var user = users.lockById(id).orElseThrow(() -> new NoSuchElementException("User not found"));
        if (id.equals(actor.getId()) || user.getRole() == User.Role.ADMIN || request.role() == User.Role.ADMIN)
            throw new IllegalArgumentException("Admin accounts cannot be changed from this page.");
        if (user.getRole() != request.role() && (user.getRole() == User.Role.STUDENT || request.role() == User.Role.STUDENT))
            throw new IllegalArgumentException("Student role changes must go through the admission workflow.");
        boolean accessChanged = user.getRole() != request.role() || user.isActive() != request.active();
        user.setFullName(request.fullName().trim()); user.setRole(request.role()); user.setActive(request.active());
        if (accessChanged) user.setTokenVersion(user.getTokenVersion() + 1);
        if (!request.active()) invitations.findByUserId(id).ifPresent(i -> i.setRevokedAt(OffsetDateTime.now()));
        audit.record(actor, "USER_UPDATED", "User", id);
        return view(user);
    }

    private UserView view(User user) {
        var invitation = invitations.findByUserId(user.getId()).orElse(null);
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(),
            !user.isActive() ? "INACTIVE" : user.isInvitationPending() ? "PENDING" : "ACTIVE",
            invitation == null ? null : invitation.getDelivery().name(), invitation == null ? null : invitation.getDeliveryMessage(),
            invitation == null ? null : invitation.getExpiresAt());
    }
}
