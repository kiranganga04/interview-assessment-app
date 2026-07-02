package com.interview.assessment.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CodingRoundDTO {
    private Long codingRoundId;
    private String skill;
    private Integer noOfQuestions;
    private Integer timeTakenMins;
    private String testComplexity;  // LOW | MEDIUM | HIGH
    private String codingStatus;    // COMPLETED | NOT_COMPLETED
    private String remarks;
}
