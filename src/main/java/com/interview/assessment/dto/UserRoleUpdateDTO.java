package com.interview.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRoleUpdateDTO {
    @NotBlank
    private String role;
    private Boolean active;
}
