package com.interview.assessment.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** One row in the Dashboard's "Today's Agenda" list. */
@Data
public class AgendaItemDTO {
    private Long interviewId;
    private String candidateName;
    private String levelOfInterview;
    private String status;
    private LocalDateTime scheduledAt;
    private String interviewerOrPanelName;
    private String modeOfInterview;
}
