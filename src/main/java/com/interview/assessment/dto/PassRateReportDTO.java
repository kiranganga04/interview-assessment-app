package com.interview.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassRateReportDTO {
    private String level;
    private long total;
    private long recommended;
    private double passRatePercent;
}
