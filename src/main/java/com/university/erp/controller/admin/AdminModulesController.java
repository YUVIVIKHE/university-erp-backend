package com.university.erp.controller.admin;

import com.university.erp.entity.User;
import com.university.erp.service.admin.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Admin-only endpoints (the /api/admin/** path is restricted to ROLE_ADMIN in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminModulesController {

    private final AdminDashboardService dashboard;
    private final NoticeService notices;
    private final LibraryService library;
    private final GradeAnalyticsService grades;
    private final InstitutionSettingsService settings;
    private final StudentImportService studentImport;
    private final AdminReportService reports;

    // ----- Dashboard -----
    @GetMapping("/dashboard")
    public AdminDashboardService.View dashboard() { return dashboard.get(); }

    // ----- Notices -----
    @GetMapping("/notices")
    public NoticeService.Page notices(@RequestParam(defaultValue = "") String search,
                                      @RequestParam(defaultValue = "") String audience,
                                      @RequestParam(defaultValue = "") String state,
                                      @RequestParam(defaultValue = "0") int page) {
        return notices.list(search, audience, state, page);
    }

    @PostMapping("/notices") @ResponseStatus(HttpStatus.CREATED)
    public NoticeService.View createNotice(@AuthenticationPrincipal User actor, @Valid @RequestBody NoticeService.Input in) {
        return notices.create(actor, in);
    }

    @PutMapping("/notices/{id}")
    public NoticeService.View updateNotice(@AuthenticationPrincipal User actor, @PathVariable long id,
                                           @Valid @RequestBody NoticeService.Input in) {
        return notices.update(actor, id, in);
    }

    @DeleteMapping("/notices/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotice(@AuthenticationPrincipal User actor, @PathVariable long id) { notices.delete(actor, id); }

    // ----- Library -----
    @GetMapping("/library/stats")
    public LibraryService.Stats libraryStats() { return library.stats(); }

    @GetMapping("/library/categories")
    public List<String> libraryCategories() { return library.categories(); }

    @GetMapping("/library/books")
    public LibraryService.PageOf<LibraryService.Book> books(@RequestParam(defaultValue = "") String search,
                                                           @RequestParam(defaultValue = "") String category,
                                                           @RequestParam(defaultValue = "false") boolean availableOnly,
                                                           @RequestParam(defaultValue = "0") int page) {
        return library.books(search, category, availableOnly, page);
    }

    @PostMapping("/library/books") @ResponseStatus(HttpStatus.CREATED)
    public LibraryService.Book createBook(@AuthenticationPrincipal User actor, @Valid @RequestBody LibraryService.BookInput in) {
        return library.createBook(actor, in);
    }

    @PutMapping("/library/books/{id}")
    public LibraryService.Book updateBook(@AuthenticationPrincipal User actor, @PathVariable long id,
                                          @Valid @RequestBody LibraryService.BookInput in) {
        return library.updateBook(actor, id, in);
    }

    @DeleteMapping("/library/books/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@AuthenticationPrincipal User actor, @PathVariable long id) { library.deleteBook(actor, id); }

    @GetMapping("/library/issues")
    public LibraryService.PageOf<LibraryService.Issue> issues(@RequestParam(defaultValue = "") String search,
                                                             @RequestParam(defaultValue = "") String status,
                                                             @RequestParam(defaultValue = "0") int page) {
        return library.issues(search, status, page);
    }

    @PostMapping("/library/issues") @ResponseStatus(HttpStatus.CREATED)
    public LibraryService.Issue issueBook(@AuthenticationPrincipal User actor, @Valid @RequestBody LibraryService.IssueInput in) {
        return library.issueBook(actor, in);
    }

    @PostMapping("/library/issues/{id}/return")
    public LibraryService.Issue returnBook(@AuthenticationPrincipal User actor, @PathVariable long id,
                                           @Valid @RequestBody(required = false) LibraryService.ReturnInput in) {
        return library.returnBook(actor, id, in);
    }

    // ----- Grades -----
    @GetMapping("/grades")
    public GradeAnalyticsService.Result grades(@RequestParam(defaultValue = "") String program,
                                               @RequestParam(required = false) Integer semester,
                                               @RequestParam(required = false) Long examId,
                                               @RequestParam(defaultValue = "") String search,
                                               @RequestParam(defaultValue = "") String outcome,
                                               @RequestParam(defaultValue = "0") int page) {
        return grades.search(program, semester, examId, search, outcome, page);
    }

    @GetMapping("/grades/options")
    public Map<String, Object> gradeOptions() {
        return Map.of("programs", grades.programs(), "exams", grades.exams());
    }

    // ----- Settings -----
    @GetMapping("/settings")
    public Map<String, String> settings() { return settings.read(); }

    @PutMapping("/settings")
    public Map<String, String> saveSettings(@AuthenticationPrincipal User actor, @RequestBody Map<String, String> in) {
        return settings.save(actor, in);
    }

    @PostMapping("/settings/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal User actor,
                               @Valid @RequestBody InstitutionSettingsService.PasswordChange in) {
        settings.changePassword(actor, in);
    }

    // ----- Bulk student import -----
    @PostMapping("/students/import")
    public StudentImportService.Response importStudents(@AuthenticationPrincipal User actor,
                                                        @Valid @RequestBody StudentImportService.Request in) {
        return studentImport.run(actor, in);
    }

    // ----- Reports -----
    @GetMapping("/reports/{type}")
    public AdminReportService.Report report(@PathVariable String type,
                                            @RequestParam(defaultValue = "") String program,
                                            @RequestParam(required = false) Integer semester,
                                            @RequestParam(defaultValue = "") String status,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) Long examId) {
        return reports.run(type, new AdminReportService.Filters(program, semester, status, from, to, examId));
    }
}
