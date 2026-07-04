package com.interview.assessment.dto;

import lombok.Data;

@Data
public class CodingRoundDTO {
    private Long codingRoundId;
    private String skill;
    private Integer noOfQuestions;
    private Integer timeTakenMins;
    private String testComplexity;
    private String codingStatus;
    private String remarks;
}
