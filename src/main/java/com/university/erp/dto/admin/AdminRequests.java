package com.university.erp.dto.admin;

import com.university.erp.entity.User;
import com.university.erp.entity.admin.EmailSettings;
import jakarta.validation.constraints.*;

public final class AdminRequests {
    private AdminRequests() {}
    public record CreateUser(@NotBlank @Size(max=255) String fullName,
                             @NotBlank @Email @Size(max=255) String email, @NotNull User.Role role) {}
    public record UpdateUser(@NotBlank @Size(max=255) String fullName, @NotNull User.Role role, @NotNull Boolean active) {}
    public record Smtp(@NotBlank @Size(max=255) String host, @Min(1) @Max(65535) int port,
                       @NotBlank @Size(max=255) String username, @Size(max=1024) String password,
                       @NotBlank @Size(max=255) String senderName, @NotBlank @Email @Size(max=255) String senderEmail,
                       @Email @Size(max=255) String replyTo, @NotNull EmailSettings.Security security,
                       @NotBlank @Size(max=255) String publicUrl, boolean enabled) {
        @Override public String toString() { return "Smtp[credentials redacted]"; }
    }
    public record TestEmail(@NotBlank @Email @Size(max=255) String recipient) {}
    public record EmailStatus(@NotNull Boolean active) {}
    public record Token(@NotBlank @Size(max=128) String token) {
        @Override public String toString() { return "Token[redacted]"; }
    }
    public record AcceptInvitation(@NotBlank @Size(max=128) String token,
                                   @NotBlank @Size(min=12, max=72) String password) {
        @Override public String toString() { return "AcceptInvitation[redacted]"; }
    }
}
