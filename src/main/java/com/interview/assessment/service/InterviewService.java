package com.interview.assessment.service;

import com.interview.assessment.dto.CodingRoundDTO;
import com.interview.assessment.dto.InterviewDTO;
import com.interview.assessment.dto.SkillAssessmentDTO;
import com.interview.assessment.entity.*;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.CandidateRepository;
import com.interview.assessment.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final CandidateRepository candidateRepository;

    public List<InterviewDTO> getAll() {
        return interviewRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public InterviewDTO getById(Long id) {
        return toDto(findEntity(id));
    }

    public List<InterviewDTO> getByCandidate(Long candidateId) {
        return interviewRepository.findByCandidate_CandidateId(candidateId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    public List<InterviewDTO> getByLevel(String level) {
        return interviewRepository.findByLevelOfInterview(InterviewLevel.valueOf(level)).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    public InterviewDTO create(InterviewDTO dto) {
        Candidate candidate = candidateRepository.findById(dto.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Candidate not found with id: " + dto.getCandidateId()));

        Interview interview = Interview.builder()
                .candidate(candidate)
                .build();
        applyFields(interview, dto);
        replaceSkillAssessments(interview, dto);
        replaceCodingRounds(interview, dto);

        return toDto(interviewRepository.save(interview));
    }

    public InterviewDTO update(Long id, InterviewDTO dto) {
        Interview interview = findEntity(id);
        applyFields(interview, dto);
        replaceSkillAssessments(interview, dto);
        replaceCodingRounds(interview, dto);
        return toDto(interviewRepository.save(interview));
    }

    public void delete(Long id) {
        interviewRepository.delete(findEntity(id));
    }

    // ---------------------------------------------------------------
    private Interview findEntity(Long id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found with id: " + id));
    }

    private void applyFields(Interview interview, InterviewDTO dto) {
        interview.setPanelMemberName(dto.getPanelMemberName());
        interview.setRecruiterName(dto.getRecruiterName());
        interview.setLevelOfInterview(dto.getLevelOfInterview() != null
                ? InterviewLevel.valueOf(dto.getLevelOfInterview()) : null);
        interview.setModeOfInterview(dto.getModeOfInterview() != null
                ? InterviewMode.valueOf(dto.getModeOfInterview()) : null);
        interview.setInterviewDate(dto.getInterviewDate());
        interview.setDomainKnowledge(dto.getDomainKnowledge());
        interview.setDomainFeedback(dto.getDomainFeedback());
        interview.setCommunicationRating(dto.getCommunicationRating());
        interview.setFinalRating(dto.getFinalRating());
        interview.setOverallAssessment(dto.getOverallAssessment());
        interview.setPanelRecommendation(dto.getPanelRecommendation());
        interview.setInterviewScreenshotUrl(dto.getInterviewScreenshotUrl());
    }

    private void replaceSkillAssessments(Interview interview, InterviewDTO dto) {
        interview.getSkillAssessments().clear();
        addSkills(interview, dto.getInternalSkillAssessments(), PanelType.INTERNAL);
        addSkills(interview, dto.getClientSkillAssessments(), PanelType.CLIENT);
    }

    private void addSkills(Interview interview, List<SkillAssessmentDTO> skills, PanelType panelType) {
        if (skills == null) return;
        for (SkillAssessmentDTO s : skills) {
            interview.getSkillAssessments().add(SkillAssessment.builder()
                    .interview(interview)
                    .panelType(panelType)
                    .skillOrder(s.getSkillOrder())
                    .skillName(s.getSkillName())
                    .selfRating(s.getSelfRating())
                    .rating(s.getRating())
                    .feedback(s.getFeedback())
                    .build());
        }
    }

    private void replaceCodingRounds(Interview interview, InterviewDTO dto) {
        interview.getCodingRounds().clear();
        if (dto.getCodingRounds() == null) return;
        for (CodingRoundDTO c : dto.getCodingRounds()) {
            interview.getCodingRounds().add(CodingRound.builder()
                    .interview(interview)
                    .skill(c.getSkill())
                    .noOfQuestions(c.getNoOfQuestions())
                    .timeTakenMins(c.getTimeTakenMins())
                    .testComplexity(c.getTestComplexity() != null
                            ? TestComplexity.valueOf(c.getTestComplexity()) : null)
                    .codingStatus(c.getCodingStatus() != null
                            ? CodingStatus.valueOf(c.getCodingStatus()) : null)
                    .remarks(c.getRemarks())
                    .build());
        }
    }

    // ---------------------------------------------------------------
    private InterviewDTO toDto(Interview i) {
        List<SkillAssessmentDTO> internal = new ArrayList<>();
        List<SkillAssessmentDTO> client = new ArrayList<>();

        for (SkillAssessment s : i.getSkillAssessments()) {
            SkillAssessmentDTO sd = SkillAssessmentDTO.builder()
                    .skillAssessmentId(s.getSkillAssessmentId())
                    .panelType(s.getPanelType().name())
                    .skillOrder(s.getSkillOrder())
                    .skillName(s.getSkillName())
                    .selfRating(s.getSelfRating())
                    .rating(s.getRating())
                    .feedback(s.getFeedback())
                    .build();
            if (s.getPanelType() == PanelType.INTERNAL) internal.add(sd); else client.add(sd);
        }

        List<CodingRoundDTO> codingRounds = i.getCodingRounds().stream()
                .map(c -> CodingRoundDTO.builder()
                        .codingRoundId(c.getCodingRoundId())
                        .skill(c.getSkill())
                        .noOfQuestions(c.getNoOfQuestions())
                        .timeTakenMins(c.getTimeTakenMins())
                        .testComplexity(c.getTestComplexity() != null ? c.getTestComplexity().name() : null)
                        .codingStatus(c.getCodingStatus() != null ? c.getCodingStatus().name() : null)
                        .remarks(c.getRemarks())
                        .build())
                .collect(Collectors.toList());

        return InterviewDTO.builder()
                .interviewId(i.getInterviewId())
                .candidateId(i.getCandidate().getCandidateId())
                .candidateName(i.getCandidate().getCandidateName())
                .mobileNumber(i.getCandidate().getMobileNumber())
                .overallExperience(i.getCandidate().getOverallExperience())
                .currentRole(i.getCandidate().getCurrentRole())
                .panelMemberName(i.getPanelMemberName())
                .recruiterName(i.getRecruiterName())
                .levelOfInterview(i.getLevelOfInterview() != null ? i.getLevelOfInterview().name() : null)
                .modeOfInterview(i.getModeOfInterview() != null ? i.getModeOfInterview().name() : null)
                .interviewDate(i.getInterviewDate())
                .domainKnowledge(i.getDomainKnowledge())
                .domainFeedback(i.getDomainFeedback())
                .communicationRating(i.getCommunicationRating())
                .finalRating(i.getFinalRating())
                .overallAssessment(i.getOverallAssessment())
                .panelRecommendation(i.getPanelRecommendation())
                .interviewScreenshotUrl(i.getInterviewScreenshotUrl())
                .internalSkillAssessments(internal)
                .clientSkillAssessments(client)
                .codingRounds(codingRounds)
                .build();
    }
}
