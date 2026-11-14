package com.university.erp.controller;

import com.university.erp.dto.*;
import com.university.erp.entity.User;
import com.university.erp.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @PreAuthorize("#req.role() == T(com.university.erp.entity.User.Role).STUDENT or hasRole('ADMIN')")
    public ResponseEntity<AuthResponse> register(
        @Valid @RequestBody RegisterRequest req,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req, request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest req,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(authService.login(req, request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserDto> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
            new AuthResponse.UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole())
        );
    }
}
