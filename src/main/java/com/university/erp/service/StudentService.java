package com.university.erp.service;

import com.university.erp.dto.StudentResponse;
import com.university.erp.dto.StudentUpdateRequest;
import com.university.erp.entity.Admission;
import com.university.erp.entity.Student;
import com.university.erp.entity.User;
import com.university.erp.repository.StudentRepository;
import com.university.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepo;
    private final UserRepository    userRepo;
    private final PasswordEncoder   passwordEncoder;

    @Transactional(readOnly = true)
    public Page<StudentResponse> list(String search, String program, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return studentRepo.search(search, program, active, pageable).map(StudentResponse::from);
    }

    @Transactional(readOnly = true)
    public StudentResponse getByRollNumber(String rollNumber) {
        return studentRepo.findByRollNumber(rollNumber)
            .map(StudentResponse::from)
            .orElseThrow(() -> new NoSuchElementException("Student not found: " + rollNumber));
    }

    @Transactional
    public StudentResponse update(String rollNumber, StudentUpdateRequest req) {
        Student s = studentRepo.findByRollNumber(rollNumber)
            .orElseThrow(() -> new NoSuchElementException("Student not found: " + rollNumber));
        if (req.program()       != null && !req.program().isBlank())  s.setProgram(req.program());
        if (req.semester()      != null) s.setSemester(req.semester());
        if (req.batchYear()     != null) s.setBatchYear(req.batchYear());
        if (req.phone()         != null) s.setPhone(req.phone().isBlank() ? null : req.phone());
        if (req.address()       != null) s.setAddress(req.address().isBlank() ? null : req.address());
        if (req.guardianName()  != null) s.setGuardianName(req.guardianName().isBlank() ? null : req.guardianName());
        if (req.guardianPhone() != null) s.setGuardianPhone(req.guardianPhone().isBlank() ? null : req.guardianPhone());
        if (req.isActive()      != null) s.setActive(req.isActive());
        if (req.dateOfBirth()   != null) s.setDateOfBirth(req.dateOfBirth().isBlank() ? null : LocalDate.parse(req.dateOfBirth()));
        return StudentResponse.from(studentRepo.save(s));
    }

    /** Called when an admission is approved — creates User + Student records. */
    @Transactional
    public StudentResponse provisionFromAdmission(Admission admission) {
        // Idempotency: if a user already exists for this email, skip user creation
        User user = userRepo.findByEmail(admission.getEmail()).orElseGet(() -> {
            User u = User.builder()
                .email(admission.getEmail())
                .fullName(admission.getFirstName() + " " + admission.getLastName())
                .passwordHash(passwordEncoder.encode(generateTemporaryPassword(admission)))
                .role(User.Role.STUDENT)
                .isActive(true)
                .build();
            return userRepo.save(u);
        });

        // If a student record already exists for this user, return it
        return studentRepo.findByUserId(user.getId())
            .map(StudentResponse::from)
            .orElseGet(() -> {
                String rollNumber = generateRollNumber(admission);
                Student s = Student.builder()
                    .user(user)
                    .rollNumber(rollNumber)
                    .program(admission.getProgram())
                    .semester(admission.getSemester())
                    .batchYear(admission.getBatchYear())
                    .dateOfBirth(admission.getDateOfBirth())
                    .phone(admission.getPhone())
                    .address(admission.getAddress())
                    .guardianName(admission.getGuardianName())
                    .guardianPhone(admission.getGuardianPhone())
                    .isActive(true)
                    .build();
                return StudentResponse.from(studentRepo.save(s));
            });
    }

    private String generateRollNumber(Admission admission) {
        int year = admission.getBatchYear() != null ? admission.getBatchYear() : Year.now().getValue();
        long count = studentRepo.count() + 1;
        String candidate;
        do {
            candidate = String.format("%d%04d", year, count++);
        } while (studentRepo.existsByRollNumber(candidate));
        return candidate;
    }

    private String generateTemporaryPassword(Admission admission) {
        // Temporary password: first name + birth year (or "0000")
        String year = admission.getDateOfBirth() != null
            ? String.valueOf(admission.getDateOfBirth().getYear()) : "0000";
        return admission.getFirstName().toLowerCase() + "@" + year;
    }
}
