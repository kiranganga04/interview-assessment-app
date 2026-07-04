package com.interview.assessment.service;

import com.interview.assessment.dto.AuthResponse;
import com.interview.assessment.dto.SignInRequest;
import com.interview.assessment.dto.SignUpRequest;
import com.interview.assessment.entity.AppUser;
import com.interview.assessment.entity.UserRole;
import com.interview.assessment.entity.UserSession;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.repository.AppUserRepository;
import com.interview.assessment.repository.PasswordResetTokenRepository;
import com.interview.assessment.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Module 10: unit tests for the security-critical AuthService — first-user-becomes-admin,
 * rejecting duplicate signups, and rejecting bad credentials on sign-in.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AuthService authService;

    /**
     * Stubs a UserSession save that returns a persisted-looking session. Only the two
     * successful-signup tests below ever reach session issuance -- the rejection tests throw
     * before getting there -- so this is called explicitly from just those two tests instead
     * of living in a shared @BeforeEach. Mockito's strict stubs (the default with
     * MockitoExtension) fail a test if a stub set up for it is never invoked during that test,
     * which is exactly what was happening: the three rejection tests were inheriting a stub
     * they never used, and Mockito correctly flagged it as dead test setup.
     */
    private void stubSessionSave() {
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            session.setSessionId(1L);
            return session;
        });
    }

    @Test
    void firstUserToSignUpBecomesAdmin() {
        stubSessionSave();

        SignUpRequest request = new SignUpRequest();
        request.setFullName("Kiran");
        request.setEmail("kiran@example.com");
        request.setPassword("password123");

        when(appUserRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(appUserRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setUserId(1L);
            return user;
        });

        AuthResponse response = authService.signUp(request);

        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN.name());
        assertThat(response.getToken()).isNotBlank();
    }

    @Test
    void secondUserToSignUpBecomesRecruiter() {
        stubSessionSave();

        SignUpRequest request = new SignUpRequest();
        request.setFullName("Second User");
        request.setEmail("second@example.com");
        request.setPassword("password123");

        when(appUserRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(appUserRepository.count()).thenReturn(5L);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setUserId(2L);
            return user;
        });

        AuthResponse response = authService.signUp(request);

        assertThat(response.getRole()).isEqualTo(UserRole.RECRUITER.name());
    }

    @Test
    void signUpRejectsDuplicateEmail() {
        SignUpRequest request = new SignUpRequest();
        request.setEmail("dup@example.com");
        request.setFullName("Dup");
        request.setPassword("password123");

        when(appUserRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void signInRejectsWrongPassword() {
        SignInRequest request = new SignInRequest();
        request.setEmail("kiran@example.com");
        request.setPassword("wrong");

        AppUser user = new AppUser();
        user.setUserId(1L);
        user.setEmail("kiran@example.com");
        user.setPasswordHash("hashed");
        user.setActive(true);
        user.setRole(UserRole.RECRUITER);

        when(appUserRepository.findByEmailIgnoreCase("kiran@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.signIn(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void signInRejectsDeactivatedUser() {
        SignInRequest request = new SignInRequest();
        request.setEmail("kiran@example.com");
        request.setPassword("password123");

        AppUser user = new AppUser();
        user.setUserId(1L);
        user.setEmail("kiran@example.com");
        user.setPasswordHash("hashed");
        user.setActive(false);
        user.setRole(UserRole.RECRUITER);

        when(appUserRepository.findByEmailIgnoreCase("kiran@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.signIn(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
