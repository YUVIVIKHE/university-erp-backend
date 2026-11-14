package com.university.erp.controller;

import com.university.erp.dto.*;
import com.university.erp.entity.Attendance;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentPortalController {

    public record StudentScholarshipOffer(
        long id,
        String scheme,
        String description,
        String category,
        String funding,
        String program,
        short minSemester,
        short maxSemester,
        short minBatchYear,
        short maxBatchYear,
        BigDecimal maxAmount,
        int academicYear,
        LocalDate applicationDeadline,
        String eligibilityCriteria,
        boolean eligible,
        String eligibilityMessage,
        String applicationStatus,
        String financialOutcome,
        BigDecimal approvedAmount
    ) {}

    public record StudentScholarshipApplicationRequest(
        @NotNull UUID requestId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal requestedAmount,
        @NotBlank @Size(max = 250) String evidenceReference,
        @NotBlank @Size(max = 2000) String reason
    ) {}

    private record ScholarshipKey(String scheme, String category, int academicYear) {}

    private final StudentRepository       studentRepo;
    private final AttendanceRepository    attendanceRepo;
    private final TimetableRepository     timetableRepo;
    private final ExamService             examService;
    private final FeeService              feeService;
    private final SubjectService          subjectService;
    private final LibraryService          libraryService;
    private final ScholarshipService      scholarshipService;
    private final NamedParameterJdbcTemplate jdbc;

    private Student resolve(User user) {
        return studentRepo.findByUserId(user.getId())
            .orElseThrow(() -> new NoSuchElementException("No student profile for this account."));
    }

    /** Own profile */
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(StudentResponse.from(resolve(user)));
    }

    /** Exam results */
    @GetMapping("/results")
    public ResponseEntity<List<ExamResultResponse>> results(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        return ResponseEntity.ok(examService.getResultsForStudent(s.getRollNumber()));
    }

    /** Results for a specific semester */
    @GetMapping("/results/semester")
    public ResponseEntity<List<ExamResultResponse>> resultsBySemester(
        @AuthenticationPrincipal User user,
        @RequestParam Short semester
    ) {
        Student s = resolve(user);
        return ResponseEntity.ok(
            examService.getResultsForStudentSemester(s.getRollNumber(), s.getProgram(), semester)
        );
    }

    /** Attendance summary per subject */
    @GetMapping("/attendance")
    public ResponseEntity<List<Map<String, Object>>> attendance(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        List<Object[]> raw = attendanceRepo.getSubjectSummaryRaw(s.getId());
        List<Map<String, Object>> rows = raw.stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("subjectCode", r[0]);
            m.put("subjectName", r[1]);
            m.put("total",       r[2]);
            m.put("present",     r[3]);
            m.put("absent",      r[4]);
            return m;
        }).toList();
        return ResponseEntity.ok(rows);
    }

    /** Fee records */
    @GetMapping("/fees")
    public ResponseEntity<List<FeeResponse>> fees(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(feeService.getFeesForStudent(user.getUsername()));
    }

    /** Subjects for the student's program (all semesters) */
    @GetMapping("/subjects")
    public ResponseEntity<Page<SubjectResponse>> subjects(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "0") int page
    ) {
        Student s = resolve(user);
        return ResponseEntity.ok(subjectService.list("", s.getProgram(), null, null, "ACTIVE", page));
    }

    /** Weekly timetable for student's program/batch/semester */
    @GetMapping("/timetable")
    public ResponseEntity<?> timetable(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        String program = s.getProgram();

        var match = timetableRepo.findByProgramOrderByBatchYearDescSemesterAsc(program).stream()
            .filter(t -> t.getBatchYear().equals(s.getBatchYear()) && t.getSemester().equals(s.getSemester()))
            .findFirst()
            .or(() -> timetableRepo.findByProgramOrderByBatchYearDescSemesterAsc(program).stream()
                .filter(t -> t.getSemester().equals(s.getSemester()))
                .findFirst());

        return match
            .map(t -> ResponseEntity.ok(TimetableResponse.from(t)))
            .orElse(ResponseEntity.ok(null));
    }

    /** All attendance records for the student (session-level detail) */
    @GetMapping("/attendance/detail")
    public ResponseEntity<List<Map<String, Object>>> attendanceDetail(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        List<Map<String, Object>> rows = attendanceRepo.findAllByStudentId(s.getId()).stream().map(a -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",          a.getId());
            m.put("subjectCode", a.getSubjectCode());
            m.put("subjectName", a.getSubjectName());
            m.put("date",        a.getDate().toString());
            m.put("timeSlot",    a.getTimeSlot());
            m.put("status",      a.getStatus().name());
            m.put("remarks",     a.getRemarks());
            return m;
        }).toList();
        return ResponseEntity.ok(rows);
    }

    /** Student term registration summary with current subject options */
    @GetMapping("/registration")
    public ResponseEntity<Map<String, Object>> registration(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        var page = subjectService.list("", s.getProgram(), Math.toIntExact(s.getSemester()), null, "ACTIVE", 0);

        List<Map<String, Object>> subjects = page.getContent().stream().map(sub -> {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("id", sub.id());
            obj.put("subjectCode", sub.subjectCode());
            obj.put("name", sub.name());
            obj.put("course", sub.course());
            obj.put("semester", sub.semester());
            obj.put("year", sub.year());
            obj.put("credits", sub.credits());
            obj.put("type", sub.type());
            obj.put("facultyName", sub.facultyName());
            return obj;
        }).toList();

        int totalCredits = page.getContent().stream().mapToInt(sub -> sub.credits() == null ? 0 : sub.credits()).sum();
        String academicYear = String.format(Locale.US, "%d-%d", s.getBatchYear(), s.getBatchYear() + 1);
        String receipt = "REG-" + s.getRollNumber() + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("academicYear", academicYear);
        payload.put("termName", "Semester Registration");
        payload.put("semester", s.getSemester());
        payload.put("studentId", s.getRollNumber());
        payload.put("rollNumber", s.getRollNumber());
        payload.put("course", s.getProgram());
        payload.put("department", s.getProgram());
        payload.put("division", "A");
        payload.put("registrationDate", LocalDate.now().toString());
        payload.put("registrationDeadline", LocalDate.now().plusDays(7).toString());
        payload.put("status", "DRAFT");
        payload.put("approvalStatus", "PENDING");
        payload.put("approvalRemarks", "Awaiting academic review.");
        payload.put("registrationReceipt", receipt);
        payload.put("selectedSubjects", page.getContent().stream().limit(4).map(SubjectResponse::subjectCode).toList());
        payload.put("availableSubjects", subjects);
        payload.put("totalCredits", Math.min(totalCredits, 28));
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/registration")
    public ResponseEntity<Map<String, Object>> registerSubjects(
        @AuthenticationPrincipal User user,
        @RequestBody Map<String, Object> body
    ) {
        Student s = resolve(user);
        Object rawSelected = body.getOrDefault("selectedSubjects", List.of());
        if (!(rawSelected instanceof List<?> selectedList) || selectedList.isEmpty()) {
            throw new IllegalArgumentException("Select at least one subject before submitting registration.");
        }

        Set<String> allowedCodes = subjectService.list("", s.getProgram(), Math.toIntExact(s.getSemester()), null, "ACTIVE", 0)
            .getContent().stream()
            .map(SubjectResponse::subjectCode)
            .collect(Collectors.toSet());

        List<String> selectedCodes = selectedList.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(allowedCodes::contains)
            .distinct()
            .toList();

        if (selectedCodes.isEmpty()) {
            throw new IllegalArgumentException("Selected subjects are not valid for your program.");
        }

        var available = subjectService.list("", s.getProgram(), Math.toIntExact(s.getSemester()), null, "ACTIVE", 0).getContent();
        int totalCredits = available.stream()
            .filter(subject -> selectedCodes.contains(subject.subjectCode()))
            .mapToInt(subject -> subject.credits() == null ? 0 : subject.credits())
            .sum();

        String receipt = "REG-" + s.getRollNumber() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        List<Map<String, Object>> subjects = available.stream().map(sub -> {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("id", sub.id());
            obj.put("subjectCode", sub.subjectCode());
            obj.put("name", sub.name());
            obj.put("course", sub.course());
            obj.put("semester", sub.semester());
            obj.put("year", sub.year());
            obj.put("credits", sub.credits());
            obj.put("type", sub.type());
            obj.put("facultyName", sub.facultyName());
            return obj;
        }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("academicYear", String.format(Locale.US, "%d-%d", s.getBatchYear(), s.getBatchYear() + 1));
        payload.put("termName", "Semester Registration");
        payload.put("semester", s.getSemester());
        payload.put("studentId", s.getRollNumber());
        payload.put("rollNumber", s.getRollNumber());
        payload.put("course", s.getProgram());
        payload.put("department", s.getProgram());
        payload.put("division", "A");
        payload.put("registrationDate", LocalDate.now().toString());
        payload.put("registrationDeadline", LocalDate.now().plusDays(7).toString());
        payload.put("status", "SUBMITTED");
        payload.put("approvalStatus", "PENDING");
        payload.put("approvalRemarks", "Submitted for academic approval.");
        payload.put("registrationReceipt", receipt);
        payload.put("selectedSubjects", selectedCodes);
        payload.put("totalCredits", totalCredits);
        payload.put("availableSubjects", subjects);
        return ResponseEntity.ok(payload);
    }

    /** Library summary and active borrowing status for the student */
    @GetMapping("/library")
    public ResponseEntity<Map<String, Object>> library(@AuthenticationPrincipal User user) {
        Student s = resolve(user);
        LibraryService.Stats stats = libraryService.stats();
        LibraryService.PageOf<LibraryService.Book> books = libraryService.books("", null, false, 0);
        List<LibraryService.Issue> currentIssues = libraryService.currentIssuesForStudent(s.getId());
        List<Map<String, Object>> issued = currentIssues.stream().map(issue -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", issue.id());
            row.put("bookTitle", issue.bookTitle());
            row.put("accessionNo", issue.accessionNo());
            row.put("issueDate", issue.issueDate());
            row.put("dueDate", issue.dueDate());
            row.put("returnDate", issue.returnDate());
            row.put("status", issue.status());
            row.put("fine", issue.fine());
            row.put("daysOverdue", issue.daysOverdue());
            row.put("estimatedFine", issue.estimatedFine());
            return row;
        }).toList();

        List<Map<String, Object>> catalog = books.content().stream()
            .limit(8)
            .map(book -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", book.id());
                row.put("title", book.title());
                row.put("author", book.author());
                row.put("category", book.category());
                row.put("accessionNo", book.accessionNo());
                row.put("totalCopies", book.totalCopies());
                row.put("availableCopies", book.availableCopies());
                row.put("shelf", book.shelf());
                return row;
            }).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", Map.of(
            "totalTitles", stats.titles(),
            "availableCopies", stats.availableCopies(),
            "borrowedByMe", issued.size(),
            "overdueByMe", currentIssues.stream().filter(LibraryService.Issue::overdue).count()
        ));
        payload.put("books", catalog);
        payload.put("issued", issued);
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/library/categories")
    public ResponseEntity<List<String>> libraryCategories(@AuthenticationPrincipal User user) {
        resolve(user);
        return ResponseEntity.ok(libraryService.categories());
    }

    @GetMapping("/library/books")
    public ResponseEntity<LibraryService.PageOf<LibraryService.Book>> libraryBooks(
        @AuthenticationPrincipal User user,
        @RequestParam(defaultValue = "") String search,
        @RequestParam(defaultValue = "") String category,
        @RequestParam(defaultValue = "false") boolean availableOnly,
        @RequestParam(defaultValue = "0") int page
    ) {
        resolve(user);
        if (search.length() > 100 || category.length() > 100 || page < 0 || page > 100000) {
            throw new IllegalArgumentException("Invalid library search or pagination parameters");
        }
        return ResponseEntity.ok(libraryService.books(search, category, availableOnly, page));
    }

    @GetMapping("/library/books/{id}")
    public ResponseEntity<LibraryService.Book> libraryBook(
        @AuthenticationPrincipal User user,
        @PathVariable long id
    ) {
        resolve(user);
        if (id < 1) throw new IllegalArgumentException("Book ID must be positive");
        return ResponseEntity.ok(libraryService.getBook(id));
    }

    /** Student scholarship and financial aid application status */
    @GetMapping("/scholarships")
    public ResponseEntity<List<Map<String, Object>>> scholarships(@AuthenticationPrincipal User user) {
        resolve(user);
        String email = user.getEmail() == null ? "" : user.getEmail().trim();

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT s.id, s.admission_id, a.first_name || ' ' || a.last_name AS student_name,
                   a.program, s.scheme, s.category, s.funding, s.academic_year,
                   s.requested_amount, s.evidence_reference, s.reason, s.status,
                   s.reviewed_by, s.review_note, s.created_at,
                   CASE WHEN EXISTS (
                        SELECT 1 FROM finance_award_reversals r
                        JOIN finance_award_decisions d ON d.id = r.decision_id
                        WHERE d.application_id = s.id AND r.status = 'APPROVED'
                   ) THEN 'REVERSED'
                        ELSE d.outcome END AS financial_outcome,
                   d.amount AS approved_amount
            FROM finance_scholarship_applications s
            JOIN admissions a ON a.admission_id = s.admission_id
            LEFT JOIN finance_award_decisions d ON d.application_id = s.id
            WHERE LOWER(a.email) = LOWER(:email)
            ORDER BY s.created_at DESC
            """,
            Map.of("email", email)
        );

        return ResponseEntity.ok(rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("admission_id", row.get("admission_id"));
            item.put("student_name", row.get("student_name"));
            item.put("program", row.get("program"));
            item.put("scheme", row.get("scheme"));
            item.put("category", row.get("category"));
            item.put("funding", row.get("funding"));
            item.put("academic_year", row.get("academic_year"));
            item.put("requested_amount", row.get("requested_amount"));
            item.put("evidence_reference", row.get("evidence_reference"));
            item.put("reason", row.get("reason"));
            item.put("status", row.get("status"));
            item.put("reviewed_by", row.get("reviewed_by"));
            item.put("review_note", row.get("review_note"));
            item.put("financial_outcome", row.get("financial_outcome"));
            item.put("approved_amount", row.get("approved_amount"));
            item.put("created_at", row.get("created_at"));
            return item;
        }).toList());
    }

    @GetMapping("/scholarships/offers")
    public ResponseEntity<List<StudentScholarshipOffer>> scholarshipOffers(@AuthenticationPrincipal User user) {
        Student student = resolve(user);
        String email = user.getEmail() == null ? "" : user.getEmail().trim();

        List<Map<String, Object>> applicationRows = jdbc.queryForList("""
            SELECT DISTINCT ON (s.scheme, s.category, s.academic_year)
                   s.scheme, s.category, s.academic_year, s.status,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM finance_award_reversals r
                       WHERE r.decision_id = d.id AND r.status = 'APPROVED'
                   ) THEN 'REVERSED' ELSE d.outcome END AS financial_outcome,
                   d.amount AS approved_amount
            FROM finance_scholarship_applications s
            JOIN admissions a ON a.admission_id = s.admission_id
            LEFT JOIN LATERAL (
                SELECT decision.outcome, decision.amount, decision.id
                FROM finance_award_decisions decision
                WHERE decision.application_id = s.id
                ORDER BY decision.created_at DESC, decision.id DESC
                LIMIT 1
            ) d ON TRUE
            WHERE LOWER(a.email) = LOWER(:email)
            ORDER BY s.scheme, s.category, s.academic_year, s.created_at DESC, s.id DESC
            """, Map.of("email", email));

        Map<ScholarshipKey, Map<String, Object>> applicationsByOffer = new HashMap<>();
        for (Map<String, Object> row : applicationRows) {
            ScholarshipKey key = new ScholarshipKey(
                (String) row.get("scheme"),
                (String) row.get("category"),
                ((Number) row.get("academic_year")).intValue()
            );
            applicationsByOffer.put(key, row);
        }

        List<StudentScholarshipOffer> offers = jdbc.queryForList("""
            SELECT id, scheme, description, category, funding, program, min_semester,
                   max_semester, min_batch_year, max_batch_year, max_amount, academic_year,
                   application_deadline, eligibility_criteria
            FROM scholarship_offers
            WHERE is_active = TRUE
            ORDER BY application_deadline NULLS LAST, scheme
            """, Map.of()).stream().map(row -> {
                String scheme = (String) row.get("scheme");
                String category = (String) row.get("category");
                int academicYear = ((Number) row.get("academic_year")).intValue();
                Map<String, Object> application = applicationsByOffer.get(
                    new ScholarshipKey(scheme, category, academicYear));
                String applicationStatus = application == null ? null : (String) application.get("status");
                String financialOutcome = application == null
                    ? null
                    : (String) application.get("financial_outcome");
                BigDecimal approvedAmount = application == null
                    ? null
                    : (BigDecimal) application.get("approved_amount");
                String program = (String) row.get("program");
                short minSemester = ((Number) row.get("min_semester")).shortValue();
                short maxSemester = ((Number) row.get("max_semester")).shortValue();
                short minBatchYear = ((Number) row.get("min_batch_year")).shortValue();
                short maxBatchYear = ((Number) row.get("max_batch_year")).shortValue();
                LocalDate deadline = row.get("application_deadline") instanceof java.sql.Date date
                    ? date.toLocalDate()
                    : (LocalDate) row.get("application_deadline");
                String ineligibility = scholarshipIneligibility(
                    student, program, minSemester, maxSemester, minBatchYear, maxBatchYear,
                    deadline, applicationStatus, financialOutcome);
                return new StudentScholarshipOffer(
                    ((Number) row.get("id")).longValue(),
                    scheme,
                    (String) row.get("description"),
                    category,
                    (String) row.get("funding"),
                    program,
                    minSemester,
                    maxSemester,
                    minBatchYear,
                    maxBatchYear,
                    (BigDecimal) row.get("max_amount"),
                    academicYear,
                    deadline,
                    (String) row.get("eligibility_criteria"),
                    ineligibility == null,
                    ineligibility == null ? "Your student profile meets the listed criteria." : ineligibility,
                    applicationStatus,
                    financialOutcome,
                    approvedAmount
                );
            }).toList();

        return ResponseEntity.ok(offers);
    }

    @PostMapping("/scholarships/offers/{offerId}/applications")
    public ResponseEntity<Map<String, Object>> applyForScholarship(
        @AuthenticationPrincipal User user,
        @PathVariable long offerId,
        @Valid @RequestBody StudentScholarshipApplicationRequest request
    ) {
        Student student = resolve(user);
        Map<String, Object> offer = jdbc.queryForList("""
            SELECT id, scheme, category, funding, program, min_semester, max_semester,
                   min_batch_year, max_batch_year, max_amount, academic_year,
                   application_deadline
            FROM scholarship_offers
            WHERE id = :id AND is_active = TRUE
            """, Map.of("id", offerId)).stream().findFirst()
            .orElseThrow(() -> new NoSuchElementException("Scholarship offer not found"));

        LocalDate deadline = offer.get("application_deadline") instanceof java.sql.Date date
            ? date.toLocalDate()
            : (LocalDate) offer.get("application_deadline");
        String ineligibility = scholarshipIneligibility(
            student,
            (String) offer.get("program"),
            ((Number) offer.get("min_semester")).shortValue(),
            ((Number) offer.get("max_semester")).shortValue(),
            ((Number) offer.get("min_batch_year")).shortValue(),
            ((Number) offer.get("max_batch_year")).shortValue(),
            deadline,
            null,
            null
        );
        if (ineligibility != null) throw new ConflictException(ineligibility);

        BigDecimal maxAmount = (BigDecimal) offer.get("max_amount");
        if (request.requestedAmount().compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("Requested amount exceeds the maximum for this scholarship.");
        }

        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        List<Map<String, Object>> admissions = jdbc.queryForList("""
            SELECT admission_id
            FROM admissions
            WHERE LOWER(email) = LOWER(:email) AND status = 'APPROVED'
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """, Map.of("email", email));
        if (admissions.isEmpty()) throw new ConflictException("An approved admission is required to apply.");

        var application = new ScholarshipService.Input(
            request.requestId(),
            (String) admissions.get(0).get("admission_id"),
            (String) offer.get("scheme"),
            (String) offer.get("category"),
            (String) offer.get("funding"),
            ((Number) offer.get("academic_year")).intValue(),
            request.requestedAmount(),
            request.evidenceReference(),
            request.reason()
        );
        return ResponseEntity.status(201).body(scholarshipService.create(application, email));
    }

    private String scholarshipIneligibility(
        Student student,
        String program,
        short minSemester,
        short maxSemester,
        short minBatchYear,
        short maxBatchYear,
        LocalDate deadline,
        String applicationStatus,
        String financialOutcome
    ) {
        if (!student.isActive()) return "Your student profile is inactive.";
        if (program != null && !program.isBlank() && !program.equalsIgnoreCase(student.getProgram())) {
            return "This offer is not available for your program.";
        }
        if (student.getSemester() < minSemester || student.getSemester() > maxSemester) {
            return "Your current semester is outside the eligible range.";
        }
        if (student.getBatchYear() < minBatchYear || student.getBatchYear() > maxBatchYear) {
            return "Your batch year is outside the eligible range.";
        }
        if (deadline != null && deadline.isBefore(LocalDate.now())) {
            return "The application deadline has passed.";
        }
        if ("POSTED".equals(financialOutcome)) return "An award has already been posted for this offer.";
        if ("SUBMITTED".equals(applicationStatus) || "VERIFIED".equals(applicationStatus)) {
            return "An application for this offer is already in progress.";
        }
        return null;
    }

    /** Update own contact/guardian info */
    @PatchMapping("/profile")
    public ResponseEntity<StudentResponse> updateProfile(
        @AuthenticationPrincipal User user,
        @RequestBody Map<String, String> body
    ) {
        Student s = resolve(user);
        if (body.containsKey("phone"))         s.setPhone(body.get("phone"));
        if (body.containsKey("address"))       s.setAddress(body.get("address"));
        if (body.containsKey("guardianName"))  s.setGuardianName(body.get("guardianName"));
        if (body.containsKey("guardianPhone")) s.setGuardianPhone(body.get("guardianPhone"));
        return ResponseEntity.ok(StudentResponse.from(studentRepo.save(s)));
    }
}
