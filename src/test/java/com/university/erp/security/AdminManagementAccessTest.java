package com.university.erp.security;

import com.university.erp.config.SecurityConfig;
import com.university.erp.controller.admin.*;
import com.university.erp.service.admin.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminUserController.class, EmailSettingsController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AdminManagementAccessTest {
    @Autowired MockMvc mvc;
    @MockBean UserDetailsService userDetailsService;
    @MockBean JwtService jwtService;
    @MockBean AdminUserService users;
    @MockBean InvitationService invitations;
    @MockBean EmailService email;

    @Test void onlyAdminCanListUsersAndReadEmailSettings() throws Exception {
        for (String path : new String[]{"/api/admin/users", "/api/admin/email-settings", "/api/admin/email-settings/accounts"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : new String[]{"ACCOUNTANT", "FACULTY", "STUDENT"})
                mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        }
    }
    @Test void nonAdminCannotSendMailOrInvitations() throws Exception {
        for (String role : new String[]{"ACCOUNTANT", "FACULTY", "STUDENT"}) {
            mvc.perform(post("/api/admin/users/1/invitation").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/email-settings/test").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/email-settings").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/email-settings/accounts").with(user("other").roles(role))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/email-settings/accounts/1/status").with(user("other").roles(role))).andExpect(status().isForbidden());
        }
    }
}
