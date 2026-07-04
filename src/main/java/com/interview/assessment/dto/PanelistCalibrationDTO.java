package com.interview.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Module 7: how a panelist's average final rating compares to the overall average,
 * to surface panelists who are consistently harsher or more lenient than their peers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PanelistCalibrationDTO {
    private String panelMemberName;
    private long interviewCount;
    private double averageFinalRating;
    private double deviationFromOverallAverage;
}
