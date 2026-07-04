package com.interview.assessment.service;

import com.interview.assessment.dto.*;
import com.interview.assessment.entity.AppUser;
import com.interview.assessment.entity.PasswordResetToken;
import com.interview.assessment.entity.UserRole;
import com.interview.assessment.entity.UserSession;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.AppUserRepository;
import com.interview.assessment.repository.PasswordResetTokenRepository;
import com.interview.assessment.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long SESSION_TTL_HOURS = 12;
    private static final long RESET_TOKEN_TTL_MINUTES = 30;

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        if (appUserRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists.");
        }
        AppUser user = new AppUser();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        // First user in the system becomes ADMIN so there's always someone who can manage the rest.
        user.setRole(appUserRepository.count() == 0 ? UserRole.ADMIN : UserRole.RECRUITER);
        user = appUserRepository.save(user);
        return issueSession(user);
    }

    @Transactional
    public AuthResponse signIn(SignInRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!user.isActive()) {
            throw new BadCredentialsException("This account has been deactivated.");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return issueSession(user);
    }

    @Transactional
    public void signOut(String token) {
        userSessionRepository.deleteByAuthToken(token);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        appUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getUserId());
            token.setToken(randomToken());
            token.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
            passwordResetTokenRepository.save(token);
            notificationService.passwordResetRequested(user.getEmail(), token.getToken());
        });
        // Deliberately no error when the email doesn't exist, so this endpoint can't be used to enumerate accounts.
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token."));
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Invalid or expired reset token.");
        }
        AppUser user = appUserRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        userSessionRepository.deleteByUserId(user.getUserId());
    }

    private AuthResponse issueSession(AppUser user) {
        UserSession session = new UserSession();
        session.setUserId(user.getUserId());
        session.setAuthToken(randomToken());
        session.setExpiresAt(LocalDateTime.now().plusHours(SESSION_TTL_HOURS));
        session = userSessionRepository.save(session);

        return new AuthResponse(
                session.getAuthToken(),
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                session.getExpiresAt());
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
