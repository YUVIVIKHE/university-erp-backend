package com.university.erp.service.admin;

import com.university.erp.entity.User;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Creates student login accounts + student profiles from spreadsheet rows.
 * dryRun=true only validates. On import, valid rows are created and invalid rows are skipped and reported.
 */
@Service
@RequiredArgsConstructor
public class StudentImportService {

    public static final int MAX_ROWS = 500;
    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("d/M/yyyy"));
    private static final char[] PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AdminAuditService audit;
    private final SecureRandom random = new SecureRandom();

    public record Row(String fullName, String email, String rollNumber, String program, String semester,
                      String batchYear, String phone, String dateOfBirth, String guardianName,
                      String guardianPhone, String address) {}

    public record Request(@NotNull @Size(min = 1, max = MAX_ROWS) List<Row> rows, boolean dryRun) {}

    public record RowResult(int row, String fullName, String email, String rollNumber, String status,
                            List<String> errors, String temporaryPassword) {}

    public record Response(boolean dryRun, int total, int valid, int invalid, int created, List<RowResult> rows) {}

    private static String t(String s) { return s == null ? "" : s.trim(); }

    @Transactional
    public Response run(User actor, Request req) {
        List<Row> rows = req.rows();
        // Existing emails / roll numbers for every row in one query each
        Set<String> emails = new HashSet<>(), rolls = new HashSet<>();
        for (Row r : rows) { emails.add(t(r.email()).toLowerCase()); rolls.add(t(r.rollNumber()).toUpperCase()); }
        Set<String> takenEmails = new HashSet<>(jdbc.queryForList(
            "SELECT LOWER(email) FROM users WHERE LOWER(email) IN (:e)", Map.of("e", emails), String.class));
        Set<String> takenRolls = new HashSet<>(jdbc.queryForList(
            "SELECT UPPER(roll_number) FROM students WHERE UPPER(roll_number) IN (:r)", Map.of("r", rolls), String.class));

        Map<String, Integer> emailSeen = new HashMap<>(), rollSeen = new HashMap<>();
        List<RowResult> results = new ArrayList<>();
        List<Object[]> toCreate = new ArrayList<>();   // {index, row, dob}
        int currentYear = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int rowNo = i + 2; // spreadsheet row number (row 1 = header)
            List<String> errs = new ArrayList<>();
            String name = t(r.fullName()), email = t(r.email()).toLowerCase(), roll = t(r.rollNumber()).toUpperCase();

            if (name.isEmpty()) errs.add("Full name is required");
            else if (name.length() > 255) errs.add("Full name is too long");
            if (email.isEmpty()) errs.add("Email is required");
            else if (!email.matches(EMAIL_RE)) errs.add("Email is not valid");
            else if (takenEmails.contains(email)) errs.add("Email already has an account");
            else if (emailSeen.containsKey(email)) errs.add("Duplicate email (also on row " + emailSeen.get(email) + ")");
            if (roll.isEmpty()) errs.add("Roll number is required");
            else if (roll.length() > 50) errs.add("Roll number is too long");
            else if (takenRolls.contains(roll)) errs.add("Roll number already exists");
            else if (rollSeen.containsKey(roll)) errs.add("Duplicate roll number (also on row " + rollSeen.get(roll) + ")");
            if (t(r.program()).isEmpty()) errs.add("Program is required");
            else if (t(r.program()).length() > 100) errs.add("Program is too long");
            Integer sem = parseInt(r.semester());
            if (sem == null || sem < 1 || sem > 12) errs.add("Semester must be 1-12");
            Integer batch = parseInt(r.batchYear());
            if (batch == null || batch < 2000 || batch > currentYear + 1) errs.add("Batch year must be 2000-" + (currentYear + 1));
            if (t(r.phone()).length() > 20) errs.add("Phone is too long");
            if (t(r.guardianPhone()).length() > 20) errs.add("Guardian phone is too long");
            if (t(r.guardianName()).length() > 255) errs.add("Guardian name is too long");
            LocalDate dob = null;
            if (!t(r.dateOfBirth()).isEmpty()) {
                dob = parseDate(t(r.dateOfBirth()));
                if (dob == null) errs.add("Date of birth must be YYYY-MM-DD or DD-MM-YYYY");
                else if (dob.isAfter(LocalDate.now())) errs.add("Date of birth is in the future");
            }

            if (!email.isEmpty()) emailSeen.putIfAbsent(email, rowNo);
            if (!roll.isEmpty()) rollSeen.putIfAbsent(roll, rowNo);

            if (errs.isEmpty()) {
                results.add(new RowResult(rowNo, name, email, roll, "VALID", List.of(), null));
                toCreate.add(new Object[]{results.size() - 1, r, dob, sem, batch});
            } else {
                results.add(new RowResult(rowNo, name, email, roll, "ERROR", errs, null));
            }
        }

        int created = 0;
        if (!req.dryRun()) {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            for (Object[] c : toCreate) {
                int idx = (int) c[0];
                Row r = (Row) c[1];
                RowResult v = results.get(idx);
                String password = generatePassword();
                var p = new MapSqlParameterSource("email", v.email()).addValue("hash", encoder.encode(password))
                    .addValue("name", v.fullName());
                Long userId = jdbc.queryForObject("""
                    INSERT INTO users (email, password_hash, full_name, role, is_active, invitation_pending, token_version, created_at, updated_at)
                    VALUES (:email, :hash, :name, 'STUDENT', TRUE, FALSE, 0, NOW(), NOW()) RETURNING id
                    """, p, Long.class);
                p.addValue("user", userId).addValue("roll", v.rollNumber()).addValue("program", t(r.program()))
                    .addValue("sem", ((Integer) c[3]).shortValue()).addValue("batch", ((Integer) c[4]).shortValue())
                    .addValue("dob", c[2]).addValue("phone", nz(r.phone())).addValue("address", nz(r.address()))
                    .addValue("gname", nz(r.guardianName())).addValue("gphone", nz(r.guardianPhone()))
                    .addValue("today", today);
                jdbc.update("""
                    INSERT INTO students (user_id, roll_number, program, semester, batch_year, date_of_birth, phone,
                                          address, guardian_name, guardian_phone, enrollment_date, is_active, created_at, updated_at)
                    VALUES (:user, :roll, :program, :sem, :batch, :dob, :phone, :address, :gname, :gphone, :today, TRUE, NOW(), NOW())
                    """, p);
                results.set(idx, new RowResult(v.row(), v.fullName(), v.email(), v.rollNumber(), "CREATED", List.of(), password));
                created++;
            }
            if (created > 0) audit.record(actor, "STUDENTS_BULK_IMPORTED", "Student", (long) created);
        }

        int valid = toCreate.size();
        return new Response(req.dryRun(), rows.size(), valid, rows.size() - valid, created, results);
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(PW_CHARS[random.nextInt(PW_CHARS.length)]);
        return sb.toString();
    }

    private static String nz(String s) { String v = t(s); return v.isEmpty() ? null : v; }

    private static Integer parseInt(String s) {
        try { return (int) Double.parseDouble(t(s)); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate parseDate(String s) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s, f); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }
}
