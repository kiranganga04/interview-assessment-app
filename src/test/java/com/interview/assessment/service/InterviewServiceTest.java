package com.interview.assessment.service;

import com.interview.assessment.dto.InterviewDTO;
import com.interview.assessment.entity.Candidate;
import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.InterviewStatus;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.repository.CandidateRepository;
import com.interview.assessment.repository.InterviewRepository;
import com.interview.assessment.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Module 10: unit tests for the workflow transition rules (module 3) InterviewService enforces. */
@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InterviewService interviewService;

    private Interview newInterview(InterviewStatus status) {
        Interview interview = new Interview();
        interview.setInterviewId(1L);
        interview.setStatus(status);
        Candidate candidate = new Candidate();
        candidate.setCandidateId(1L);
        candidate.setCandidateName("Test Candidate");
        interview.setCandidate(candidate);
        return interview;
    }

    @Test
    void allowsScheduledToInProgress() {
        Interview interview = newInterview(InterviewStatus.SCHEDULED);
        when(interviewRepository.findById(1L)).thenReturn(Optional.of(interview));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewDTO result = interviewService.changeStatus(1L, "IN_PROGRESS");

        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
        verify(auditService).record(eq("Interview"), eq(1L), eq("STATUS_CHANGE"), any());
    }

    @Test
    void rejectsSkippingStraightToClosedFromScheduledIsActuallyAllowedButRecommendedFromScheduledIsNot() {
        // SCHEDULED -> CLOSED is a valid "cancel" path, but SCHEDULED -> RECOMMENDED must go through the
        // intermediate states rather than jumping straight to a final recommendation.
        Interview interview = newInterview(InterviewStatus.SCHEDULED);
        when(interviewRepository.findById(1L)).thenReturn(Optional.of(interview));

        assertThatThrownBy(() -> interviewService.changeStatus(1L, "RECOMMENDED"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsAnyTransitionOutOfClosed() {
        Interview interview = newInterview(InterviewStatus.CLOSED);
        when(interviewRepository.findById(1L)).thenReturn(Optional.of(interview));

        assertThatThrownBy(() -> interviewService.changeStatus(1L, "IN_PROGRESS"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnknownStatus() {
        Interview interview = newInterview(InterviewStatus.SCHEDULED);
        when(interviewRepository.findById(1L)).thenReturn(Optional.of(interview));

        assertThatThrownBy(() -> interviewService.changeStatus(1L, "NOT_A_REAL_STATUS"))
                .isInstanceOf(BadRequestException.class);
    }
}
