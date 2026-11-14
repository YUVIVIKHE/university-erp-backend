package com.university.erp.controller;

import com.university.erp.entity.Student;
import com.university.erp.entity.User;
import com.university.erp.repository.AttendanceRepository;
import com.university.erp.repository.StudentRepository;
import com.university.erp.repository.TimetableRepository;
import com.university.erp.service.ExamService;
import com.university.erp.service.FeeService;
import com.university.erp.service.SubjectService;
import com.university.erp.service.admin.LibraryService;
import com.university.erp.service.finance.ScholarshipService;
import com.university.erp.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentPortalControllerTest {

    private final StudentRepository students = mock(StudentRepository.class);
    private final LibraryService library = mock(LibraryService.class);
    private final ScholarshipService scholarships = mock(ScholarshipService.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final User user = User.builder()
        .id(41L)
        .email("student@example.com")
        .fullName("Alex Example")
        .build();
    private final Student student = Student.builder()
        .id(73L)
        .user(user)
        .rollNumber("ST-0073")
        .program("Computer Science")
        .semester((short) 2)
        .batchYear((short) 2025)
        .phone("555-0100")
        .isActive(true)
        .build();
    private final StudentPortalController controller = new StudentPortalController(
        students,
        mock(AttendanceRepository.class),
        mock(TimetableRepository.class),
        mock(ExamService.class),
        mock(FeeService.class),
        mock(SubjectService.class),
        library,
        scholarships,
        jdbc
    );

    @BeforeEach
    void setUp() {
        when(students.findByUserId(user.getId())).thenReturn(Optional.of(student));
    }

    @Test
    void librarySummaryUsesGlobalCatalogTotalsAndOnlyThisStudentsCurrentLoans() {
        when(library.stats()).thenReturn(new LibraryService.Stats(
            33, 75, 58, 12, 4, BigDecimal.ZERO, BigDecimal.ZERO));
        when(library.books("", null, false, 0)).thenReturn(
            new LibraryService.PageOf<>(List.of(new LibraryService.Book(
                1L, "A-001", null, "Intro to Computing", "A. Author", null, "Computing",
                "A1", 1, 1, 0)), 33, 2, 0));
        when(library.currentIssuesForStudent(73L)).thenReturn(List.of(
            issue(1L, true, 5),
            issue(2L, false, 0)
        ));

        Map<String, Object> response = controller.library(user).getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) response.get("summary");

        assertEquals(33L, summary.get("totalTitles"));
        assertEquals(58L, summary.get("availableCopies"));
        assertEquals(2, summary.get("borrowedByMe"));
        assertEquals(1L, summary.get("overdueByMe"));
        verify(library).currentIssuesForStudent(73L);
    }

    @Test
    void scholarshipHistoryIsScopedOnlyByTheAuthenticatedAccountEmail() {
        doReturn(List.of()).when(jdbc).queryForList(anyString(), anyMap());

        controller.scholarships(user);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(Map.of("email", "student@example.com")));
        assertTrue(sql.getValue().contains("WHERE LOWER(a.email) = LOWER(:email)"));
        assertFalse(sql.getValue().contains(" OR "));
    }

    @Test
    void scholarshipOfferEligibilityUsesStudentProfileFields() {
        Map<String, Object> offer = new HashMap<>();
        offer.put("id", 19L);
        offer.put("scheme", "Continuing Grant");
        offer.put("description", "A profile-based demo grant.");
        offer.put("category", "SCHOLARSHIP");
        offer.put("funding", "INSTITUTION");
        offer.put("program", "Computer Science");
        offer.put("min_semester", (short) 1);
        offer.put("max_semester", (short) 4);
        offer.put("min_batch_year", (short) 2024);
        offer.put("max_batch_year", (short) 2026);
        offer.put("max_amount", BigDecimal.valueOf(20000));
        offer.put("academic_year", 2026);
        offer.put("application_deadline", java.sql.Date.valueOf(LocalDate.now().plusDays(10)));
        offer.put("eligibility_criteria", "Active students in semesters 1-4.");
        when(jdbc.queryForList(anyString(), anyMap()))
            .thenReturn(List.of(), List.of(offer));

        var result = controller.scholarshipOffers(user).getBody();

        assertEquals(1, result.size());
        assertTrue(result.get(0).eligible());
        assertEquals("Your student profile meets the listed criteria.", result.get(0).eligibilityMessage());
    }

    @Test
    void scholarshipApplicationRejectsProfileOutsideOfferSemesterRange() {
        Map<String, Object> offer = new HashMap<>();
        offer.put("scheme", "Advanced Grant");
        offer.put("category", "SCHOLARSHIP");
        offer.put("funding", "INSTITUTION");
        offer.put("program", null);
        offer.put("min_semester", (short) 3);
        offer.put("max_semester", (short) 8);
        offer.put("min_batch_year", (short) 2024);
        offer.put("max_batch_year", (short) 2026);
        offer.put("max_amount", BigDecimal.valueOf(20000));
        offer.put("academic_year", 2026);
        offer.put("application_deadline", java.sql.Date.valueOf(LocalDate.now().plusDays(10)));
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(offer));

        assertThrows(ConflictException.class, () -> controller.applyForScholarship(
            user,
            19L,
            new StudentPortalController.StudentScholarshipApplicationRequest(
                java.util.UUID.randomUUID(),
                BigDecimal.valueOf(1000),
                "evidence-1",
                "Applying for educational support."
            )
        ));
    }

    @Test
    void studentLibraryCatalogUsesSearchCategoryAvailabilityAndPageFilters() {
        var result = new LibraryService.PageOf<>(
            List.of(book(2L)), 21, 2, 1);
        when(library.books("algorithms", "Computing", true, 1)).thenReturn(result);

        assertEquals(result, controller.libraryBooks(user, "algorithms", "Computing", true, 1).getBody());

        verify(library).books("algorithms", "Computing", true, 1);
    }

    @Test
    void studentCanReadBookDetailsAndCategories() {
        when(library.categories()).thenReturn(List.of("Computing", "Mathematics"));
        when(library.getBook(2L)).thenReturn(book(2L));

        assertEquals(List.of("Computing", "Mathematics"), controller.libraryCategories(user).getBody());
        assertEquals(book(2L), controller.libraryBook(user, 2L).getBody());
    }

    @Test
    void studentLibraryRejectsOversizedSearchAndInvalidPages() {
        assertThrows(IllegalArgumentException.class,
            () -> controller.libraryBooks(user, "x".repeat(101), "", false, 0));
        assertThrows(IllegalArgumentException.class,
            () -> controller.libraryBooks(user, "", "", false, -1));
    }

    private LibraryService.Book book(long id) {
        return new LibraryService.Book(
            id, "A-" + id, "9780000000000", "Intro to Computing", "A. Author",
            "University Press", "Computing", "A1", 3, 2, 1
        );
    }

    private LibraryService.Issue issue(long id, boolean overdue, long daysOverdue) {
        return new LibraryService.Issue(
            id, id, "Book " + id, "A-00" + id, 73L, "ST-0073", "Alex Example",
            "Computer Science", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
            null, "ISSUED", overdue, daysOverdue, BigDecimal.ZERO, BigDecimal.valueOf(daysOverdue * 12), null
        );
    }
}