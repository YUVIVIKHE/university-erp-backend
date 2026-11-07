package com.university.erp.service.admin;

import com.university.erp.dto.admin.AdminRequests;
import com.university.erp.entity.User;
import com.university.erp.repository.*;
import com.university.erp.repository.admin.UserInvitationRepository;
import com.university.erp.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AdmissionRepository admissions = mock(AdmissionRepository.class);
    private final StudentService students = mock(StudentService.class);
    private final UserInvitationRepository invitations = mock(UserInvitationRepository.class);
    private final AdminUserService service = new AdminUserService(users, admissions, students, invitations,
        mock(InvitationService.class), mock(PasswordEncoder.class), mock(AdminAuditService.class));
    private final User admin = User.builder().id(99L).role(User.Role.ADMIN).build();

    @Test void studentRequiresApprovedAdmission() {
        when(admissions.findFirstByEmailIgnoreCaseAndStatus(anyString(), any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.create(new AdminRequests.CreateUser("Student", "student@example.com", User.Role.STUDENT), admin));
        verify(users, never()).saveAndFlush(any()); verifyNoInteractions(students);
    }
    @Test void roleChangeRevokesSessionsAndCannotConvertStudentOrAdmin() {
        var user = User.builder().id(1L).fullName("Staff").role(User.Role.FACULTY).isActive(true).build();
        when(users.lockById(1L)).thenReturn(Optional.of(user));
        when(invitations.findByUserId(1L)).thenReturn(Optional.empty());
        service.update(1L, new AdminRequests.UpdateUser("Staff", User.Role.ACCOUNTANT, true), admin);
        assertEquals(1, user.getTokenVersion()); assertEquals(User.Role.ACCOUNTANT, user.getRole());
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, new AdminRequests.UpdateUser("Staff", User.Role.STUDENT, true), admin));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, new AdminRequests.UpdateUser("Staff", User.Role.ADMIN, true), admin));
    }
}
