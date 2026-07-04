package com.interview.assessment.service;

import com.interview.assessment.dto.AuthResponse;
import com.interview.assessment.dto.SignInRequest;
import com.interview.assessment.dto.SignUpRequest;
import com.interview.assessment.entity.AppUser;
import com.interview.assessment.entity.UserRole;
import com.interview.assessment.entity.UserSession;
import com.interview.assessment.repository.AppUserRepository;
import com.interview.assessment.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final int SESSION_DAYS = 7;

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse signUp(SignUpRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email.");
        }

        AppUser user = AppUser.builder()
                .fullName(request.getFullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.RECRUITER)
                .active(true)
                .build();

        return createSession(appUserRepository.save(user));
    }

    public AuthResponse signIn(SignInRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> invalidCredentials());

        if (!user.isActive() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return createSession(user);
    }

    private AuthResponse createSession(AppUser user) {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(SESSION_DAYS);
        UserSession session = userSessionRepository.save(UserSession.builder()
                .user(user)
                .authToken(UUID.randomUUID().toString())
                .expiresAt(expiresAt)
                .build());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .token(session.getAuthToken())
                .expiresAt(expiresAt)
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }
}
