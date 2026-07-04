package com.interview.assessment.service;

import com.interview.assessment.dto.*;
import com.interview.assessment.entity.*;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.CandidateRepository;
import com.interview.assessment.repository.InterviewRepository;
import com.interview.assessment.repository.SkillRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewService {

    /** Module 3: the only allowed forward/back moves in the assessment workflow. */
    private static final Map<InterviewStatus, Set<InterviewStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(InterviewStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(InterviewStatus.SCHEDULED, Set.of(InterviewStatus.IN_PROGRESS, InterviewStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.IN_PROGRESS, Set.of(InterviewStatus.SUBMITTED, InterviewStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.SUBMITTED, Set.of(InterviewStatus.RECOMMENDED, InterviewStatus.IN_PROGRESS, InterviewStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.RECOMMENDED, Set.of(InterviewStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.CLOSED, Set.of());
    }

    private final InterviewRepository interviewRepository;
    private final CandidateRepository candidateRepository;
    private final SkillRepository skillRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<InterviewDTO> search(String levelOfInterview, String status, String search, Pageable pageable) {
        Specification<Interview> spec = buildSpecification(levelOfInterview, status, search);
        Page<Interview> page = interviewRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public InterviewDTO get(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public InterviewDTO create(InterviewDTO dto) {
        Interview interview = new Interview();
        interview.setCandidate(findCandidate(dto.getCandidateId()));
        applyFields(interview, dto);
        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "CREATE",
                "level=" + interview.getLevelOfInterview() + " candidateId=" + dto.getCandidateId());
        if (interview.getScheduledAt() != null) {
            notificationService.interviewScheduled(interview.getRecruiterName(),
                    interview.getCandidate().getCandidateName(), interview.getScheduledAt().toString());
        }
        return toDto(interview);
    }

    @Transactional
    public InterviewDTO update(Long id, InterviewDTO dto) {
        Interview interview = findOrThrow(id);
        if (dto.getCandidateId() != null && !dto.getCandidateId().equals(interview.getCandidate().getCandidateId())) {
            interview.setCandidate(findCandidate(dto.getCandidateId()));
        }
        applyFields(interview, dto);
        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "UPDATE", "final rating updated");
        return toDto(interview);
    }

    @Transactional
    public InterviewDTO changeStatus(Long id, String requestedStatus) {
        Interview interview = findOrThrow(id);
        InterviewStatus next = parseStatus(requestedStatus);
        Set<InterviewStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(interview.getStatus(), Set.of());
        if (!allowed.contains(next)) {
            throw new BadRequestException(
                    "Cannot move an interview from " + interview.getStatus() + " to " + next + ".");
        }
        interview.setStatus(next);
        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "STATUS_CHANGE", next.name());
        notificationService.interviewStatusChanged(interview.getRecruiterName(),
                interview.getCandidate().getCandidateName(), next.name());
        return toDto(interview);
    }

    @Transactional
    public void delete(Long id) {
        Interview interview = findOrThrow(id);
        auditService.record("Interview", interview.getInterviewId(), "DELETE",
                "candidate=" + interview.getCandidate().getCandidateName());
        interviewRepository.delete(interview);
    }

    // ---------- internals ----------

    private Specification<Interview> buildSpecification(String levelOfInterview, String status, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(levelOfInterview)) {
                predicates.add(cb.equal(root.get("levelOfInterview"), InterviewLevel.valueOf(levelOfInterview.toUpperCase())));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), InterviewStatus.valueOf(status.toUpperCase())));
            }
            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("candidate").get("candidateName")), like),
                        cb.like(cb.lower(root.get("panelMemberName")), like),
                        cb.like(cb.lower(root.get("recruiterName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private InterviewStatus parseStatus(String raw) {
        try {
            return InterviewStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown status: " + raw);
        }
    }

    private Candidate findCandidate(Long candidateId) {
        if (candidateId == null) {
            throw new BadRequestException("candidateId is required.");
        }
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));
    }

    private Interview findOrThrow(Long id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
    }

    private void applyFields(Interview interview, InterviewDTO dto) {
        interview.setPanelMemberName(dto.getPanelMemberName());
        interview.setRecruiterName(dto.getRecruiterName());
        if (StringUtils.hasText(dto.getLevelOfInterview())) {
            interview.setLevelOfInterview(InterviewLevel.valueOf(dto.getLevelOfInterview().toUpperCase()));
        }
        if (StringUtils.hasText(dto.getModeOfInterview())) {
            interview.setModeOfInterview(InterviewMode.valueOf(dto.getModeOfInterview().toUpperCase()));
        }
        interview.setInterviewDate(dto.getInterviewDate());
        interview.setDomainKnowledge(dto.getDomainKnowledge());
        interview.setDomainFeedback(dto.getDomainFeedback());
        interview.setCommunicationRating(dto.getCommunicationRating());
        interview.setFinalRating(dto.getFinalRating());
        interview.setOverallAssessment(dto.getOverallAssessment());
        interview.setPanelRecommendation(dto.getPanelRecommendation());
        interview.setInterviewScreenshotUrl(dto.getInterviewScreenshotUrl());
        interview.setScheduledAt(dto.getScheduledAt());
        if (StringUtils.hasText(dto.getStatus())) {
            // Only honour a status on create; transitions afterwards must go through changeStatus()
            // so the allowed-transition rules above can't be bypassed by a plain PUT.
            if (interview.getInterviewId() == null) {
                interview.setStatus(parseStatus(dto.getStatus()));
            }
        }

        replaceSkillAssessments(interview, dto.getInternalSkillAssessments(), PanelType.INTERNAL);
        replaceSkillAssessments(interview, dto.getClientSkillAssessments(), PanelType.CLIENT);
        replaceCodingRounds(interview, dto.getCodingRounds());
    }

    private void replaceSkillAssessments(Interview interview, List<SkillAssessmentDTO> dtos, PanelType panelType) {
        interview.getSkillAssessments().removeIf(sa -> sa.getPanelType() == panelType);
        if (dtos == null) {
            return;
        }
        for (SkillAssessmentDTO dto : dtos) {
            SkillAssessment entity = new SkillAssessment();
            entity.setInterview(interview);
            entity.setPanelType(panelType);
            entity.setSkillOrder(dto.getSkillOrder());
            entity.setSkillName(dto.getSkillName());
            entity.setSelfRating(dto.getSelfRating());
            entity.setRating(dto.getRating());
            entity.setFeedback(dto.getFeedback());
            if (dto.getSkillId() != null) {
                skillRepository.findById(dto.getSkillId()).ifPresent(entity::setSkill);
            }
            interview.getSkillAssessments().add(entity);
        }
    }

    private void replaceCodingRounds(Interview interview, List<CodingRoundDTO> dtos) {
        interview.getCodingRounds().clear();
        if (dtos == null) {
            return;
        }
        for (CodingRoundDTO dto : dtos) {
            CodingRound entity = new CodingRound();
            entity.setInterview(interview);
            entity.setSkill(dto.getSkill());
            entity.setNoOfQuestions(dto.getNoOfQuestions());
            entity.setTimeTakenMins(dto.getTimeTakenMins());
            if (StringUtils.hasText(dto.getTestComplexity())) {
                entity.setTestComplexity(TestComplexity.valueOf(dto.getTestComplexity().toUpperCase()));
            }
            if (StringUtils.hasText(dto.getCodingStatus())) {
                entity.setCodingStatus(CodingStatus.valueOf(dto.getCodingStatus().toUpperCase()));
            }
            entity.setRemarks(dto.getRemarks());
            interview.getCodingRounds().add(entity);
        }
    }

    private InterviewDTO toDto(Interview interview) {
        InterviewDTO dto = new InterviewDTO();
        dto.setInterviewId(interview.getInterviewId());
        dto.setCandidateId(interview.getCandidate().getCandidateId());
        dto.setCandidateName(interview.getCandidate().getCandidateName());
        dto.setCurrentRole(interview.getCandidate().getCurrentRole());
        dto.setOverallExperience(interview.getCandidate().getOverallExperience());
        dto.setPanelMemberName(interview.getPanelMemberName());
        dto.setRecruiterName(interview.getRecruiterName());
        dto.setLevelOfInterview(interview.getLevelOfInterview() != null ? interview.getLevelOfInterview().name() : null);
        dto.setModeOfInterview(interview.getModeOfInterview() != null ? interview.getModeOfInterview().name() : null);
        dto.setInterviewDate(interview.getInterviewDate());
        dto.setDomainKnowledge(interview.getDomainKnowledge());
        dto.setDomainFeedback(interview.getDomainFeedback());
        dto.setCommunicationRating(interview.getCommunicationRating());
        dto.setFinalRating(interview.getFinalRating());
        dto.setOverallAssessment(interview.getOverallAssessment());
        dto.setPanelRecommendation(interview.getPanelRecommendation());
        dto.setInterviewScreenshotUrl(interview.getInterviewScreenshotUrl());
        dto.setStatus(interview.getStatus() != null ? interview.getStatus().name() : null);
        dto.setScheduledAt(interview.getScheduledAt());

        dto.setInternalSkillAssessments(interview.getSkillAssessments().stream()
                .filter(sa -> sa.getPanelType() == PanelType.INTERNAL)
                .sorted((a, b) -> Integer.compare(a.getSkillOrder(), b.getSkillOrder()))
                .map(this::toSkillDto)
                .collect(Collectors.toList()));
        dto.setClientSkillAssessments(interview.getSkillAssessments().stream()
                .filter(sa -> sa.getPanelType() == PanelType.CLIENT)
                .sorted((a, b) -> Integer.compare(a.getSkillOrder(), b.getSkillOrder()))
                .map(this::toSkillDto)
                .collect(Collectors.toList()));
        dto.setCodingRounds(interview.getCodingRounds().stream().map(this::toCodingDto).collect(Collectors.toList()));
        return dto;
    }

    private SkillAssessmentDTO toSkillDto(SkillAssessment entity) {
        SkillAssessmentDTO dto = new SkillAssessmentDTO();
        dto.setSkillAssessmentId(entity.getSkillAssessmentId());
        dto.setSkillOrder(entity.getSkillOrder());
        dto.setSkillId(entity.getSkill() != null ? entity.getSkill().getSkillId() : null);
        dto.setSkillName(entity.getSkillName());
        dto.setSelfRating(entity.getSelfRating());
        dto.setRating(entity.getRating());
        dto.setFeedback(entity.getFeedback());
        return dto;
    }

    private CodingRoundDTO toCodingDto(CodingRound entity) {
        CodingRoundDTO dto = new CodingRoundDTO();
        dto.setCodingRoundId(entity.getCodingRoundId());
        dto.setSkill(entity.getSkill());
        dto.setNoOfQuestions(entity.getNoOfQuestions());
        dto.setTimeTakenMins(entity.getTimeTakenMins());
        dto.setTestComplexity(entity.getTestComplexity() != null ? entity.getTestComplexity().name() : null);
        dto.setCodingStatus(entity.getCodingStatus() != null ? entity.getCodingStatus().name() : null);
        dto.setRemarks(entity.getRemarks());
        return dto;
    }
}
