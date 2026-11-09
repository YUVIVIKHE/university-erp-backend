package com.university.erp.service;

import com.university.erp.dto.FacultyRequest;
import com.university.erp.dto.FacultyResponse;
import com.university.erp.entity.Faculty;
import com.university.erp.entity.User;
import com.university.erp.exception.ConflictException;
import com.university.erp.repository.FacultyRepository;
import com.university.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class FacultyService {

    private final FacultyRepository facultyRepo;
    private final UserRepository    userRepo;
    private final PasswordEncoder   passwordEncoder;

    @Transactional(readOnly = true)
    public Page<FacultyResponse> list(String search, String department, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return facultyRepo.search(search, department, active, pageable).map(FacultyResponse::from);
    }

    @Transactional(readOnly = true)
    public FacultyResponse getById(Long id) {
        return facultyRepo.findById(id)
            .map(FacultyResponse::from)
            .orElseThrow(() -> new NoSuchElementException("Faculty not found: " + id));
    }

    @Transactional
    public FacultyResponse create(FacultyRequest req) {
        String email = req.email().trim().toLowerCase();

        if (userRepo.existsByEmailIgnoreCase(email)) {
            // If user already exists and is FACULTY role, link them
            User existing = userRepo.findByEmailIgnoreCase(email).orElseThrow();
            if (existing.getRole() != User.Role.FACULTY)
                throw new ConflictException("A user with this email exists but is not a faculty account.");
            if (facultyRepo.findByUserId(existing.getId()).isPresent())
                throw new ConflictException("A faculty profile already exists for this email.");
            Faculty faculty = buildFaculty(req, existing);
            return FacultyResponse.from(facultyRepo.save(faculty));
        }

        // Generate readable temp password: first name + "@Faculty" + last 4 of phone (or 1234)
        String firstName = req.fullName().trim().split("\\s+")[0].toLowerCase();
        String phoneSuffix = (req.phone() != null && req.phone().length() >= 4)
            ? req.phone().replaceAll("\\D", "")
            : "1234";
        phoneSuffix = phoneSuffix.length() >= 4 ? phoneSuffix.substring(phoneSuffix.length() - 4) : "1234";
        String tempPassword = firstName + "@Faculty" + phoneSuffix;

        // Create new user account
        User user = User.builder()
            .email(email)
            .fullName(req.fullName().trim())
            .role(User.Role.FACULTY)
            .passwordHash(passwordEncoder.encode(tempPassword))
            .isActive(true)
            .invitationPending(false)
            .build();
        userRepo.saveAndFlush(user);

        Faculty faculty = buildFaculty(req, user);
        return FacultyResponse.from(facultyRepo.save(faculty), tempPassword);
    }

    @Transactional
    public FacultyResponse update(Long id, FacultyRequest req) {
        Faculty f = facultyRepo.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Faculty not found: " + id));

        User user = f.getUser();
        if (req.fullName() != null && !req.fullName().isBlank())
            user.setFullName(req.fullName().trim());
        userRepo.save(user);

        if (req.department()     != null && !req.department().isBlank())     f.setDepartment(req.department());
        if (req.designation()    != null && !req.designation().isBlank())    f.setDesignation(req.designation());
        if (req.specialization() != null) f.setSpecialization(req.specialization().isBlank() ? null : req.specialization());
        if (req.qualification()  != null) f.setQualification(req.qualification().isBlank() ? null : req.qualification());
        if (req.phone()          != null) f.setPhone(req.phone().isBlank() ? null : req.phone());
        if (req.address()        != null) f.setAddress(req.address().isBlank() ? null : req.address());
        if (req.isActive()       != null) f.setActive(req.isActive());
        if (req.dateOfJoining()  != null) f.setDateOfJoining(req.dateOfJoining().isBlank() ? null : LocalDate.parse(req.dateOfJoining()));
        if (req.dateOfBirth()    != null) f.setDateOfBirth(req.dateOfBirth().isBlank() ? null : LocalDate.parse(req.dateOfBirth()));

        return FacultyResponse.from(facultyRepo.save(f));
    }

    private Faculty buildFaculty(FacultyRequest req, User user) {
        String empId = generateEmployeeId();
        return Faculty.builder()
            .user(user)
            .employeeId(empId)
            .department(req.department())
            .designation(req.designation() != null ? req.designation() : "Assistant Professor")
            .specialization(req.specialization())
            .qualification(req.qualification())
            .phone(req.phone())
            .address(req.address())
            .isActive(req.isActive() != null ? req.isActive() : true)
            .dateOfJoining(req.dateOfJoining() != null && !req.dateOfJoining().isBlank() ? LocalDate.parse(req.dateOfJoining()) : null)
            .dateOfBirth(req.dateOfBirth() != null && !req.dateOfBirth().isBlank() ? LocalDate.parse(req.dateOfBirth()) : null)
            .build();
    }

    private String generateEmployeeId() {
        int year = LocalDate.now().getYear();
        long seq  = facultyRepo.count() + 1;
        String candidate;
        do {
            candidate = "FAC" + year + String.format("%04d", seq++);
        } while (facultyRepo.existsByEmployeeId(candidate));
        return candidate;
    }
}
