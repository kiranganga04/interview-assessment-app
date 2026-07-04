package com.interview.assessment.dto;

import lombok.Data;

@Data
public class UserSummaryDTO {
    private Long userId;
    private String fullName;
    private String email;
    private String role;
    private boolean active;
}
