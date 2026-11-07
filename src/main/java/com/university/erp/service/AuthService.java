package com.university.erp.service;

import com.university.erp.dto.*;
import com.university.erp.entity.User;
import com.university.erp.exception.ConflictException;
import com.university.erp.repository.UserRepository;
import com.university.erp.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository      userRepo;
    private final PasswordEncoder     encoder;
    private final JwtService          jwtService;
    private final AuthenticationManager authManager;
    private final AuditLogService     auditLog;

    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletRequest request) {
        if (userRepo.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered: " + req.email());
        }

        User user = User.builder()
            .email(req.email())
            .passwordHash(encoder.encode(req.password()))
            .fullName(req.fullName())
            .role(req.role())
            .isActive(true)
            .build();

        userRepo.save(user);
        auditLog.log(user, "USER_REGISTERED", request);

        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req, HttpServletRequest request) {
        authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User user = userRepo.findByEmail(req.email()).orElseThrow();
        auditLog.log(user, "USER_LOGIN", request);

        return buildResponse(user);
    }

    public AuthResponse refresh(RefreshTokenRequest req) {
        String email = jwtService.extractUsername(req.refreshToken());
        User   user  = userRepo.findByEmail(email).orElseThrow();

        if (!jwtService.hasType(req.refreshToken(), "refresh") || !jwtService.isValid(req.refreshToken(), user)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        return buildResponse(user);
    }

    private AuthResponse buildResponse(User user) {
        String access  = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        AuthResponse.UserDto dto = new AuthResponse.UserDto(
            user.getId(), user.getEmail(), user.getFullName(), user.getRole()
        );

        return new AuthResponse(access, refresh, "Bearer", jwtService.getExpirationMs(), dto);
    }
}
