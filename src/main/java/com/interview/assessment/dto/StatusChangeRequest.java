package com.interview.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StatusChangeRequest {
    @NotBlank
    private String status;
}
