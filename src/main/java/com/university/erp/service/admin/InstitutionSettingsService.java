package com.university.erp.service.admin;

import com.university.erp.entity.User;
import com.university.erp.exception.ConflictException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Institution-wide settings stored as key/value rows, with typed defaults and validation. */
@Service
@RequiredArgsConstructor
public class InstitutionSettingsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditService audit;
    private final PasswordEncoder passwords;

    private record Def(String fallback, Predicate<String> valid, String rule) {}

    private static Predicate<String> maxLen(int n) { return v -> v.length() <= n; }
    private static Predicate<String> intRange(int min, int max) {
        return v -> { try { int i = Integer.parseInt(v.trim()); return i >= min && i <= max; } catch (NumberFormatException e) { return false; } };
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    static {
        DEFS.put("institutionName",     new Def("University ERP", v -> !v.isBlank() && v.length() <= 150, "required, max 150 characters"));
        DEFS.put("shortName",           new Def("UERP", maxLen(30), "max 30 characters"));
        DEFS.put("address",             new Def("", maxLen(500), "max 500 characters"));
        DEFS.put("phone",               new Def("", maxLen(30), "max 30 characters"));
        DEFS.put("email",               new Def("", v -> v.isEmpty() || v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"), "must be a valid email"));
        DEFS.put("website",             new Def("", maxLen(200), "max 200 characters"));
        DEFS.put("academicYear",        new Def("2026-27", v -> v.matches("^\\d{4}-\\d{2}$"), "format YYYY-YY, e.g. 2026-27"));
        DEFS.put("attendanceThreshold", new Def("75", intRange(0, 100), "whole number 0-100"));
        DEFS.put("libraryLoanDays",     new Def("14", intRange(1, 365), "whole number 1-365"));
        DEFS.put("libraryFinePerDay",   new Def("5", intRange(0, 1000), "whole number 0-1000"));
        DEFS.put("libraryMaxBooks",     new Def("3", intRange(1, 20), "whole number 1-20"));
    }

    @Transactional(readOnly = true)
    public Map<String, String> read() {
        Map<String, String> out = new LinkedHashMap<>();
        DEFS.forEach((k, d) -> out.put(k, d.fallback()));
        jdbc.query("SELECT setting_key, setting_value FROM institution_settings", rs -> {
            String key = rs.getString(1);
            if (out.containsKey(key)) out.put(key, rs.getString(2));
        });
        return out;
    }

    public String get(String key) { return read().get(key); }
    public int getInt(String key) { return Integer.parseInt(get(key).trim()); }

    @Transactional
    public Map<String, String> save(User actor, Map<String, String> input) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("No settings provided");
        for (var e : input.entrySet()) {
            Def def = DEFS.get(e.getKey());
            if (def == null) throw new IllegalArgumentException("Unknown setting: " + e.getKey());
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (!def.valid().test(value)) throw new IllegalArgumentException(e.getKey() + ": " + def.rule());
        }
        for (var e : input.entrySet()) {
            jdbc.update("""
                INSERT INTO institution_settings (setting_key, setting_value, updated_by, updated_at)
                VALUES (:k, :v, :u, NOW())
                ON CONFLICT (setting_key) DO UPDATE SET setting_value = :v, updated_by = :u, updated_at = NOW()
                """, new MapSqlParameterSource("k", e.getKey())
                    .addValue("v", e.getValue() == null ? "" : e.getValue().trim())
                    .addValue("u", actor.getId()));
        }
        audit.record(actor, "SETTINGS_UPDATED", "InstitutionSettings", null);
        return read();
    }

    public record PasswordChange(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {}

    /** Same rules as the accountant password change: verifies current password and invalidates other sessions. */
    @Transactional
    public void changePassword(User actor, PasswordChange input) {
        if (input.newPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("New password must be at most 72 UTF-8 bytes");
        var p = new MapSqlParameterSource("id", actor.getId());
        var row = jdbc.queryForMap("SELECT password_hash, token_version FROM users WHERE id = :id FOR UPDATE", p);
        if (!passwords.matches(input.currentPassword(), (String) row.get("password_hash")))
            throw new IllegalArgumentException("Current password is incorrect");
        if (passwords.matches(input.newPassword(), (String) row.get("password_hash")))
            throw new IllegalArgumentException("Choose a different new password");
        if (((Number) row.get("token_version")).intValue() != actor.getTokenVersion())
            throw new ConflictException("Session changed. Sign in again before changing your password.");
        p.addValue("hash", passwords.encode(input.newPassword()));
        jdbc.update("UPDATE users SET password_hash = :hash, token_version = token_version + 1, updated_at = NOW() WHERE id = :id", p);
        audit.record(actor, "ADMIN_PASSWORD_CHANGED", "User", actor.getId());
    }
}
