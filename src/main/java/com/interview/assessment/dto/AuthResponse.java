package com.interview.assessment.dto;

import com.interview.assessment.entity.UserRole;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private Long userId;
    private String fullName;
    private String email;
    private UserRole role;
    private String token;
    private LocalDateTime expiresAt;
}
