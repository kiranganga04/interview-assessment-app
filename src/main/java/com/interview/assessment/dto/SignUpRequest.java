package com.interview.assessment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SignUpRequest {
    @NotBlank(message = "Full name is mandatory")
    private String fullName;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is mandatory")
    private String email;

    @Size(min = 8, message = "Password must be at least 8 characters")
    @NotBlank(message = "Password is mandatory")
    private String password;
}
