package com.interview.assessment.service;

import com.interview.assessment.dto.CreateUserRequest;
import com.interview.assessment.dto.UserRoleUpdateDTO;
import com.interview.assessment.dto.UserSummaryDTO;
import com.interview.assessment.entity.AppUser;
import com.interview.assessment.entity.UserRole;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.AppUserRepository;
import com.interview.assessment.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Module 2: admin-only user directory + role/active management. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserSummaryDTO> listAll() {
        return appUserRepository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Admin-provisioned account creation, with the role chosen up front (ADMIN/RECRUITER/PANEL).
     * This is distinct from the public self-registration flow (AuthService.signUp), which never
     * accepts a role from the caller -- that endpoint is unauthenticated, so letting a caller pick
     * their own role there would let anyone self-assign ADMIN. Here the caller is already an
     * authenticated ADMIN (enforced by @PreAuthorize on UserController), so an explicit role choice
     * is safe.
     */
    @Transactional
    public UserSummaryDTO createUser(CreateUserRequest request) {
        if (appUserRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists.");
        }
        UserRole role;
        try {
            role = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown role: " + request.getRole());
        }
        AppUser user = new AppUser();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setActive(true);
        user = appUserRepository.save(user);
        auditService.record("AppUser", user.getUserId(), "CREATE", "role=" + user.getRole());
        return toDto(user);
    }

    @Transactional
    public UserSummaryDTO updateRole(Long userId, UserRoleUpdateDTO update) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        try {
            user.setRole(UserRole.valueOf(update.getRole().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown role: " + update.getRole());
        }
        if (update.getActive() != null) {
            user.setActive(update.getActive());
            if (!update.getActive()) {
                userSessionRepository.deleteByUserId(user.getUserId());
            }
        }
        user = appUserRepository.save(user);
        auditService.record("AppUser", user.getUserId(), "UPDATE", "role=" + user.getRole() + " active=" + user.isActive());
        return toDto(user);
    }

    private UserSummaryDTO toDto(AppUser user) {
        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setUserId(user.getUserId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().name());
        dto.setActive(user.isActive());
        return dto;
    }
}
