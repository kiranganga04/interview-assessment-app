package com.interview.assessment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin-only "add user" request (POST /api/users). Unlike SignUpRequest (public self-registration,
 * which always assigns ADMIN-if-first-user/else-PANEL and never lets the caller choose), this
 * lets an already-authenticated ADMIN create an account with any role up front -- ADMIN, RECRUITER,
 * or PANEL -- instead of having to create it via signup and then flip the role afterwards in the
 * Users table.
 */
@Data
public class CreateUserRequest {
    @NotBlank
    private String fullName;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank
    private String role;
}
