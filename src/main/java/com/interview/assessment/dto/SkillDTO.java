package com.interview.assessment.dto;

import lombok.Data;

@Data
public class SkillDTO {
    private Long skillId;
    private String name;
    private String applicableLevels;
    private boolean active = true;
}
