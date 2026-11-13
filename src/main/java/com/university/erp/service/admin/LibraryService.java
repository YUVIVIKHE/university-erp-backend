package com.university.erp.service.admin;

import com.university.erp.entity.User;
import com.university.erp.exception.ConflictException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditService audit;
    private final InstitutionSettingsService settings;

    // ---------- Books ----------

    public record BookInput(
        @NotBlank @Size(max = 40) String accessionNo,
        @Size(max = 20) String isbn,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String author,
        @Size(max = 255) String publisher,
        @Size(max = 100) String category,
        @Size(max = 50) String shelf,
        @NotNull @Min(0) @Max(10000) Integer totalCopies) {}

    public record Book(Long id, String accessionNo, String isbn, String title, String author, String publisher,
                       String category, String shelf, int totalCopies, int availableCopies, int issuedCopies) {}

    public record PageOf<T>(List<T> content, long totalElements, int totalPages, int number) {}

    private static final RowMapper<Book> BOOK = (rs, i) -> new Book(
        rs.getLong("id"), rs.getString("accession_no"), rs.getString("isbn"), rs.getString("title"),
        rs.getString("author"), rs.getString("publisher"), rs.getString("category"), rs.getString("shelf"),
        rs.getInt("total_copies"), rs.getInt("available_copies"),
        rs.getInt("total_copies") - rs.getInt("available_copies"));

    private static String like(String s) { return "%" + (s == null ? "" : s.trim().toLowerCase()) + "%"; }

    @Transactional(readOnly = true)
    public PageOf<Book> books(String search, String category, boolean availableOnly, int page) {
        int size = 20;
        var p = new MapSqlParameterSource("s", like(search))
            .addValue("cat", category == null ? "" : category)
            .addValue("avail", availableOnly)
            .addValue("limit", size).addValue("offset", Math.max(page, 0) * size);
        String where = """
            FROM library_books WHERE (LOWER(title) LIKE :s OR LOWER(author) LIKE :s
                 OR LOWER(accession_no) LIKE :s OR LOWER(COALESCE(isbn,'')) LIKE :s)
              AND (:cat = '' OR category = :cat)
              AND (:avail = FALSE OR available_copies > 0)
            """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + where, p, Long.class);
        var rows = jdbc.query("SELECT * " + where + " ORDER BY title LIMIT :limit OFFSET :offset", p, BOOK);
        return new PageOf<>(rows, total, (int) Math.ceil(total / (double) size), Math.max(page, 0));
    }

    public List<String> categories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM library_books WHERE category IS NOT NULL AND category <> '' ORDER BY 1",
            Map.of(), String.class);
    }

    private Book book(long id) {
        try {
            return jdbc.queryForObject("SELECT * FROM library_books WHERE id = :id", Map.of("id", id), BOOK);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("Book not found: " + id);
        }
    }

    private MapSqlParameterSource bookParams(BookInput in) {
        return new MapSqlParameterSource("acc", in.accessionNo().trim().toUpperCase())
            .addValue("isbn", blank(in.isbn())).addValue("title", in.title().trim())
            .addValue("author", in.author().trim()).addValue("publisher", blank(in.publisher()))
            .addValue("category", blank(in.category())).addValue("shelf", blank(in.shelf()))
            .addValue("total", in.totalCopies());
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    @Transactional
    public Book createBook(User actor, BookInput in) {
        var p = bookParams(in);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM library_books WHERE accession_no = :acc", p, Integer.class) > 0)
            throw new ConflictException("Accession number already exists: " + p.getValue("acc"));
        Long id = jdbc.queryForObject("""
            INSERT INTO library_books (accession_no, isbn, title, author, publisher, category, shelf, total_copies, available_copies)
            VALUES (:acc, :isbn, :title, :author, :publisher, :category, :shelf, :total, :total) RETURNING id
            """, p, Long.class);
        audit.record(actor, "LIBRARY_BOOK_CREATED", "LibraryBook", id);
        return book(id);
    }

    @Transactional
    public Book updateBook(User actor, long id, BookInput in) {
        var current = jdbc.query("SELECT * FROM library_books WHERE id = :id FOR UPDATE", Map.of("id", id), BOOK);
        if (current.isEmpty()) throw new NoSuchElementException("Book not found: " + id);
        int issued = current.get(0).issuedCopies();
        if (in.totalCopies() < issued)
            throw new IllegalArgumentException("Total copies cannot be less than the " + issued + " copies currently issued");
        var p = bookParams(in).addValue("id", id).addValue("avail", in.totalCopies() - issued);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM library_books WHERE accession_no = :acc AND id <> :id", p, Integer.class) > 0)
            throw new ConflictException("Accession number already exists: " + p.getValue("acc"));
        jdbc.update("""
            UPDATE library_books SET accession_no = :acc, isbn = :isbn, title = :title, author = :author,
                   publisher = :publisher, category = :category, shelf = :shelf,
                   total_copies = :total, available_copies = :avail, updated_at = NOW()
            WHERE id = :id
            """, p);
        audit.record(actor, "LIBRARY_BOOK_UPDATED", "LibraryBook", id);
        return book(id);
    }

    @Transactional
    public void deleteBook(User actor, long id) {
        book(id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM library_issues WHERE book_id = :id", Map.of("id", id), Integer.class) > 0)
            throw new ConflictException("This book has issue history and cannot be deleted. Set its copies to 0 instead.");
        jdbc.update("DELETE FROM library_books WHERE id = :id", Map.of("id", id));
        audit.record(actor, "LIBRARY_BOOK_DELETED", "LibraryBook", id);
    }

    // ---------- Issues ----------

    public record IssueInput(@NotNull Long bookId, @NotBlank String rollNumber, LocalDate dueDate,
                             @Size(max = 500) String remarks) {}

    public record Issue(Long id, Long bookId, String bookTitle, String accessionNo, Long studentId, String rollNumber,
                        String studentName, String program, LocalDate issueDate, LocalDate dueDate, LocalDate returnDate,
                        String status, boolean overdue, long daysOverdue, BigDecimal fine, BigDecimal estimatedFine,
                        String remarks) {}

    private RowMapper<Issue> issueMapper(int finePerDay) {
        LocalDate today = LocalDate.now(ZONE);
        return (rs, i) -> {
            LocalDate due = rs.getObject("due_date", LocalDate.class);
            LocalDate ret = rs.getObject("return_date", LocalDate.class);
            String status = rs.getString("status");
            long late = Math.max(0, ChronoUnit.DAYS.between(due, ret != null ? ret : today));
            boolean open = "ISSUED".equals(status);
            return new Issue(rs.getLong("id"), rs.getLong("book_id"), rs.getString("title"), rs.getString("accession_no"),
                rs.getLong("student_id"), rs.getString("roll_number"), rs.getString("full_name"), rs.getString("program"),
                rs.getObject("issue_date", LocalDate.class), due, ret, status, open && late > 0, late,
                rs.getBigDecimal("fine"), open ? BigDecimal.valueOf(late * finePerDay) : rs.getBigDecimal("fine"),
                rs.getString("remarks"));
        };
    }

    private static final String ISSUE_FROM = """
        FROM library_issues li
        JOIN library_books b ON b.id = li.book_id
        JOIN students s ON s.id = li.student_id
        JOIN users u ON u.id = s.user_id
        """;

    /** status: ISSUED | RETURNED | OVERDUE | "" */
    @Transactional(readOnly = true)
    public PageOf<Issue> issues(String search, String status, int page) {
        int size = 20;
        var p = new MapSqlParameterSource("s", like(search)).addValue("st", status == null ? "" : status)
            .addValue("today", LocalDate.now(ZONE))
            .addValue("limit", size).addValue("offset", Math.max(page, 0) * size);
        String where = ISSUE_FROM + """
            WHERE (LOWER(b.title) LIKE :s OR LOWER(s.roll_number) LIKE :s OR LOWER(u.full_name) LIKE :s OR LOWER(b.accession_no) LIKE :s)
              AND (:st = '' OR (:st = 'OVERDUE' AND li.status = 'ISSUED' AND li.due_date < :today)
                   OR (:st <> 'OVERDUE' AND li.status = :st))
            """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + where, p, Long.class);
        var rows = jdbc.query("SELECT li.*, b.title, b.accession_no, s.roll_number, s.program, u.full_name " + where
            + " ORDER BY (li.status = 'ISSUED') DESC, li.due_date ASC, li.id DESC LIMIT :limit OFFSET :offset",
            p, issueMapper(settings.getInt("libraryFinePerDay")));
        return new PageOf<>(rows, total, (int) Math.ceil(total / (double) size), Math.max(page, 0));
    }

    private Issue issue(long id) {
        var rows = jdbc.query("SELECT li.*, b.title, b.accession_no, s.roll_number, s.program, u.full_name "
            + ISSUE_FROM + " WHERE li.id = :id", Map.of("id", id), issueMapper(settings.getInt("libraryFinePerDay")));
        if (rows.isEmpty()) throw new NoSuchElementException("Issue record not found: " + id);
        return rows.get(0);
    }

    @Transactional
    public Issue issueBook(User actor, IssueInput in) {
        LocalDate today = LocalDate.now(ZONE);
        var bookRows = jdbc.query("SELECT * FROM library_books WHERE id = :id FOR UPDATE", Map.of("id", in.bookId()), BOOK);
        if (bookRows.isEmpty()) throw new NoSuchElementException("Book not found: " + in.bookId());
        if (bookRows.get(0).availableCopies() <= 0) throw new ConflictException("No copies of this book are available");

        var students = jdbc.queryForList("SELECT id, is_active FROM students WHERE UPPER(roll_number) = UPPER(:r)",
            Map.of("r", in.rollNumber().trim()));
        if (students.isEmpty()) throw new NoSuchElementException("No student with roll number " + in.rollNumber().trim());
        if (!Boolean.TRUE.equals(students.get(0).get("is_active"))) throw new IllegalArgumentException("Student is inactive");
        long studentId = ((Number) students.get(0).get("id")).longValue();

        var p = new MapSqlParameterSource("book", in.bookId()).addValue("student", studentId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM library_issues WHERE book_id = :book AND student_id = :student AND status = 'ISSUED'", p, Integer.class) > 0)
            throw new ConflictException("This student already has a copy of this book");
        int max = settings.getInt("libraryMaxBooks");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM library_issues WHERE student_id = :student AND status = 'ISSUED'", p, Integer.class) >= max)
            throw new ConflictException("Student already holds the maximum of " + max + " books");

        LocalDate due = in.dueDate() != null ? in.dueDate() : today.plusDays(settings.getInt("libraryLoanDays"));
        if (due.isBefore(today)) throw new IllegalArgumentException("Due date cannot be in the past");
        p.addValue("today", today).addValue("due", due).addValue("remarks", blank(in.remarks())).addValue("by", actor.getId());
        Long id = jdbc.queryForObject("""
            INSERT INTO library_issues (book_id, student_id, issue_date, due_date, remarks, issued_by)
            VALUES (:book, :student, :today, :due, :remarks, :by) RETURNING id
            """, p, Long.class);
        jdbc.update("UPDATE library_books SET available_copies = available_copies - 1, updated_at = NOW() WHERE id = :book", p);
        audit.record(actor, "LIBRARY_BOOK_ISSUED", "LibraryIssue", id);
        return issue(id);
    }

    public record ReturnInput(Boolean waiveFine, @Size(max = 500) String remarks) {}

    @Transactional
    public Issue returnBook(User actor, long id, ReturnInput in) {
        var rows = jdbc.queryForList("SELECT book_id, due_date, status FROM library_issues WHERE id = :id FOR UPDATE", Map.of("id", id));
        if (rows.isEmpty()) throw new NoSuchElementException("Issue record not found: " + id);
        if (!"ISSUED".equals(rows.get(0).get("status"))) throw new ConflictException("This book has already been returned");
        LocalDate today = LocalDate.now(ZONE);
        LocalDate due = ((java.sql.Date) rows.get(0).get("due_date")).toLocalDate();
        long late = Math.max(0, ChronoUnit.DAYS.between(due, today));
        boolean waive = in != null && Boolean.TRUE.equals(in.waiveFine());
        BigDecimal fine = waive ? BigDecimal.ZERO : BigDecimal.valueOf(late * settings.getInt("libraryFinePerDay"));
        var p = new MapSqlParameterSource("id", id).addValue("today", today).addValue("fine", fine)
            .addValue("by", actor.getId()).addValue("book", rows.get(0).get("book_id"))
            .addValue("remarks", in == null ? null : blank(in.remarks()));
        jdbc.update("""
            UPDATE library_issues SET status = 'RETURNED', return_date = :today, fine = :fine, returned_by = :by,
                   remarks = COALESCE(:remarks, remarks), updated_at = NOW()
            WHERE id = :id
            """, p);
        jdbc.update("UPDATE library_books SET available_copies = available_copies + 1, updated_at = NOW() WHERE id = :book", p);
        audit.record(actor, "LIBRARY_BOOK_RETURNED", "LibraryIssue", id);
        return issue(id);
    }

    public record Stats(long titles, long totalCopies, long availableCopies, long issued, long overdue,
                        BigDecimal finesCollected, BigDecimal finesPending) {}

    @Transactional(readOnly = true)
    public Stats stats() {
        LocalDate today = LocalDate.now(ZONE);
        int finePerDay = settings.getInt("libraryFinePerDay");
        var b = jdbc.queryForMap("SELECT COUNT(*) t, COALESCE(SUM(total_copies),0) c, COALESCE(SUM(available_copies),0) a FROM library_books", Map.of());
        var i = jdbc.queryForMap("""
            SELECT COUNT(*) FILTER (WHERE status = 'ISSUED') issued,
                   COUNT(*) FILTER (WHERE status = 'ISSUED' AND due_date < :today) overdue,
                   COALESCE(SUM(fine) FILTER (WHERE status = 'RETURNED'), 0) collected,
                   COALESCE(SUM(:today - due_date) FILTER (WHERE status = 'ISSUED' AND due_date < :today), 0) late_days
            FROM library_issues
            """, Map.of("today", today));
        return new Stats(n(b.get("t")), n(b.get("c")), n(b.get("a")), n(i.get("issued")), n(i.get("overdue")),
            (BigDecimal) i.get("collected"), BigDecimal.valueOf(n(i.get("late_days")) * finePerDay));
    }

    private static long n(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
}
