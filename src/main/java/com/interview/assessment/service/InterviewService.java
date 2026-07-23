package com.interview.assessment.service;

import com.interview.assessment.config.FileStorageProperties;
import com.interview.assessment.dto.*;
import com.interview.assessment.entity.*;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.AttachmentRepository;
import com.interview.assessment.repository.CandidateRepository;
import com.interview.assessment.repository.InterviewRepository;
import com.interview.assessment.repository.InterviewSpecifications;
import com.interview.assessment.repository.InterviewSlotRepository;
import com.interview.assessment.repository.InterviewerRepository;
import com.interview.assessment.repository.SkillRepository;
import com.interview.assessment.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
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
        ALLOWED_TRANSITIONS.put(InterviewStatus.SCHEDULED, Set.of(InterviewStatus.IN_PROGRESS, InterviewStatus.CLOSED, InterviewStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.IN_PROGRESS, Set.of(InterviewStatus.SUBMITTED, InterviewStatus.CLOSED, InterviewStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.SUBMITTED, Set.of(InterviewStatus.RECOMMENDED, InterviewStatus.IN_PROGRESS, InterviewStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.RECOMMENDED, Set.of(InterviewStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(InterviewStatus.CLOSED, Set.of());
        ALLOWED_TRANSITIONS.put(InterviewStatus.CANCELLED, Set.of());
    }

    private final InterviewRepository interviewRepository;
    private final CandidateRepository candidateRepository;
    private final SkillRepository skillRepository;
    private final InterviewSlotRepository interviewSlotRepository;
    private final InterviewerRepository interviewerRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AttachmentRepository attachmentRepository;
    private final FileStorageProperties fileStorageProperties;

    @Transactional(readOnly = true)
    public PageResponse<InterviewDTO> search(String levelOfInterview, String status, String search, Pageable pageable) {
        // RBAC: a RECRUITER only ever lists their own assessments; PANEL never reaches this
        // endpoint (controller-gated); ADMIN sees everything. The visibility predicate is ANDed
        // with the user's level/status/search filters so scoping can't be filtered away.
        Specification<Interview> spec = InterviewSpecifications.visibleToCurrentUser()
                .and(buildSpecification(levelOfInterview, status, search));
        Page<Interview> page = interviewRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public InterviewDTO get(Long id) {
        Interview interview = findOrThrow(id);
        enforceCanAccess(interview);
        return toDto(interview);
    }

    /**
     * Attachments: lets FileStorageService apply the exact same record-ownership rule to
     * INTERVIEW_SCREENSHOT attachments (upload, list, download) as already applies to the
     * interview record itself, without duplicating the ownership-matching logic in two places.
     * Throws if the interview doesn't exist, or if the caller is a PANEL user who isn't the
     * assigned interviewer, or a RECRUITER who neither created nor is assigned to it; a no-op for ADMIN.
     */
    @Transactional(readOnly = true)
    public void assertCanAccessInterview(Long interviewId) {
        enforceCanAccess(findOrThrow(interviewId));
    }

    /**
     * Feedback & Reports: a Panel member's "to-do list" -- only interviews where they're the
     * assigned interviewer (matched by login email against the Interviewers directory, the only
     * link between the two today -- see Interviewer.java) and still awaiting feedback (not yet
     * SUBMITTED/RECOMMENDED/CLOSED/CANCELLED). If their login email doesn't match any Interviewer
     * directory row, they simply have no assigned interviews -- an empty list, not an error.
     */
    @Transactional(readOnly = true)
    public List<InterviewDTO> myOpenInterviews() {
        String callerEmail = CurrentUser.emailOrSystem();
        return interviewerRepository.findByEmailIgnoreCase(callerEmail)
                .map(interviewer -> interviewRepository.findByInterviewerAndStatusIn(
                        interviewer, List.of(InterviewStatus.SCHEDULED, InterviewStatus.IN_PROGRESS)))
                .orElseGet(List::of)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Feedback & Reports: a Panel member's full interview history -- every interview assigned to
     * them as the interviewer regardless of status (scheduled, in-progress, submitted, closed,
     * cancelled), most recent first. Backs the "My Interview History" page. Same email-to-directory
     * resolution as myOpenInterviews(): an unmatched login email yields an empty list, not an error.
     */
    @Transactional(readOnly = true)
    public List<InterviewDTO> myInterviewHistory() {
        String callerEmail = CurrentUser.emailOrSystem();
        return interviewerRepository.findByEmailIgnoreCase(callerEmail)
                .map(interviewRepository::findByInterviewerOrderByInterviewIdDesc)
                .orElseGet(List::of)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
            // One email to whoever we have an address for. The plain "New assessment" flow has no
            // interviewer/slot, so only candidate + recruiter are populated here; blank addresses
            // are skipped inside the notification service. The candidate's resume (if already on
            // file) is attached.
            notificationService.interviewScheduled(emailDetails(interview), candidateResumeAttachments(interview.getCandidate()));
        }
        return toDto(interview);
    }

    /**
     * Interview Management: books an AVAILABLE slot (flips it to BOOKED) and creates a
     * SCHEDULED interview from it -- interviewer, date/time and mode all come from the
     * slot rather than being re-entered. This is additive to the plain create() above
     * (still used by the "New assessment" form, including by PANEL); existing callers
     * of create() are unaffected.
     */
    @Transactional
    public InterviewDTO scheduleFromSlot(ScheduleInterviewRequest request) {
        InterviewSlot slot = interviewSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Interview slot not found: " + request.getSlotId()));
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new BadRequestException("This slot is no longer available -- please pick another.");
        }

        Candidate candidate = findCandidate(request.getCandidateId());
        if (!StringUtils.hasText(candidate.getEmail())) {
            throw new BadRequestException(
                    "This candidate doesn't have an email on file yet. Add one from the Candidates page "
                            + "(or enter a new candidate with an email) before scheduling.");
        }
        // Meeting link only makes sense for an online session -- an in-person or phone slot has
        // nowhere to put it, so it's only required when the slot's mode is VIRTUAL.
        if (slot.getMode() == InterviewMode.VIRTUAL && !StringUtils.hasText(request.getMeetingLink())) {
            throw new BadRequestException("A meeting link is required for online interviews.");
        }

        Interview interview = new Interview();
        interview.setCandidate(candidate);
        interview.setInterviewer(slot.getInterviewer());
        interview.setSlot(slot);
        interview.setPanelMemberName(slot.getInterviewer().getFullName());
        interview.setRecruiterName(request.getRecruiterName());
        interview.setRecruiterEmail(request.getRecruiterEmail());
        interview.setPosition(request.getPosition());
        interview.setLevelOfInterview(parseLevel(request.getLevelOfInterview()));
        interview.setModeOfInterview(slot.getMode());
        interview.setMeetingLink(request.getMeetingLink());
        interview.setInterviewDate(slot.getSlotDate());
        interview.setScheduledAt(slot.getSlotDate().atTime(slot.getStartTime()));
        interview.setStatus(InterviewStatus.SCHEDULED);

        slot.setStatus(SlotStatus.BOOKED);
        interviewSlotRepository.save(slot);

        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "SCHEDULE",
                "slot=" + slot.getSlotCode() + " interviewer=" + slot.getInterviewer().getFullName());

        notifyScheduleParticipants(interview);
        return toDto(interview);
    }

    /**
     * Sends ONE email addressed to the candidate, interviewer and recruiter together (previously
     * three separate messages) with the full session details, including the meeting link when
     * there is one. Blank addresses are skipped and a send failure is logged and swallowed inside
     * EmailNotificationService, so it never blocks the scheduling transaction.
     */
    private void notifyScheduleParticipants(Interview interview) {
        notificationService.interviewScheduled(emailDetails(interview), candidateResumeAttachments(interview.getCandidate()));
    }

    /**
     * Resolves the candidate's resume for attaching to the scheduling email. Returns the single most
     * recently uploaded CANDIDATE_RESUME (by attachment id) as an on-disk path, or an empty list when
     * the candidate has no resume. The Schedule Interview flow uploads the resume BEFORE calling
     * scheduleFromSlot, so it's already on file here.
     */
    private List<NotificationService.EmailAttachment> candidateResumeAttachments(Candidate candidate) {
        if (candidate == null || candidate.getCandidateId() == null) {
            return List.of();
        }
        return attachmentRepository
                .findByOwnerTypeAndOwnerId(AttachmentOwnerType.CANDIDATE_RESUME, candidate.getCandidateId())
                .stream()
                .max(java.util.Comparator.comparing(Attachment::getAttachmentId))
                .map(a -> List.of(new NotificationService.EmailAttachment(
                        a.getOriginalFilename(),
                        java.nio.file.Paths.get(fileStorageProperties.getDirectory(), a.getStoredFilename()).toString())))
                .orElseGet(List::of);
    }

    /**
     * Builds the shared email payload from an interview -- used by the scheduled, rescheduled and
     * cancelled notifications so all three address the same candidate/interviewer/recruiter set.
     * interviewer may be null (plain "New assessment" records have no slot/interviewer); the panel
     * member's free-text name is used as the display fallback in that case.
     */
    private NotificationService.InterviewEmailDetails emailDetails(Interview interview) {
        Candidate candidate = interview.getCandidate();
        Interviewer interviewer = interview.getInterviewer();
        return new NotificationService.InterviewEmailDetails(
                interview.getInterviewId(),
                candidate != null ? candidate.getEmail() : null,
                candidate != null ? candidate.getCandidateName() : null,
                interviewer != null ? interviewer.getEmail() : null,
                interviewer != null ? interviewer.getFullName() : interview.getPanelMemberName(),
                interview.getRecruiterEmail(),
                interview.getRecruiterName(),
                interview.getPosition(),
                interview.getLevelOfInterview() != null ? interview.getLevelOfInterview().name() : null,
                interview.getModeOfInterview() != null ? interview.getModeOfInterview().name() : null,
                interview.getScheduledAt() != null ? interview.getScheduledAt().toString() : null,
                interview.getMeetingLink());
    }

    @Transactional
    public InterviewDTO update(Long id, InterviewDTO dto) {
        Interview interview = findOrThrow(id);
        enforceCanAccess(interview);
        if (dto.getCandidateId() != null && !dto.getCandidateId().equals(interview.getCandidate().getCandidateId())) {
            interview.setCandidate(findCandidate(dto.getCandidateId()));
        }
        applyFields(interview, dto);
        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "UPDATE", "final rating updated");
        return toDto(interview);
    }

    /**
     * Explicit reschedule (distinct from a plain edit): moves the interview's date/time and, when
     * provided, updates the meeting link. Allowed for the assigned panel member, the owning
     * recruiter, or an admin (same ownership rule as everything else), and only while the interview
     * is still live -- a CANCELLED or CLOSED interview can't be rescheduled. Records a RESCHEDULE
     * audit entry and emails the candidate, interviewer and recruiter the new time in one message.
     */
    @Transactional
    public InterviewDTO reschedule(Long id, RescheduleRequest request) {
        Interview interview = findOrThrow(id);
        enforceCanAccess(interview);
        if (interview.getStatus() == InterviewStatus.CANCELLED || interview.getStatus() == InterviewStatus.CLOSED) {
            throw new BadRequestException("A " + interview.getStatus().name().toLowerCase()
                    + " interview can't be rescheduled.");
        }

        java.time.LocalDateTime previous = interview.getScheduledAt();
        interview.setScheduledAt(request.getScheduledAt());
        interview.setInterviewDate(request.getScheduledAt().toLocalDate());
        if (StringUtils.hasText(request.getMeetingLink())) {
            interview.setMeetingLink(request.getMeetingLink());
        }
        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "RESCHEDULE",
                "from=" + (previous != null ? previous : "-") + " to=" + request.getScheduledAt()
                        + (StringUtils.hasText(request.getReason()) ? " reason=" + request.getReason() : ""));
        notificationService.interviewRescheduled(emailDetails(interview),
                previous != null ? previous.toString() : null);
        return toDto(interview);
    }

    @Transactional
    public InterviewDTO changeStatus(Long id, String requestedStatus) {
        Interview interview = findOrThrow(id);
        enforceCanAccess(interview);
        InterviewStatus next = parseStatus(requestedStatus);
        Set<InterviewStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(interview.getStatus(), Set.of());
        if (!allowed.contains(next)) {
            throw new BadRequestException(
                    "Cannot move an interview from " + interview.getStatus() + " to " + next + ".");
        }
        interview.setStatus(next);
        if (next == InterviewStatus.CANCELLED) {
            releaseSlotIfAny(interview);
        }
        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "STATUS_CHANGE", next.name());
        if (next == InterviewStatus.CANCELLED) {
            // A cancellation notifies everyone involved (candidate + interviewer + recruiter) with a
            // dedicated cancellation email, not just the recruiter with a generic status line.
            notificationService.interviewCancelled(emailDetails(interview));
        } else if (next == InterviewStatus.SUBMITTED) {
            // Panel submitted their feedback -> notify the recruiter with a link to review it.
            notificationService.interviewFeedbackSubmitted(emailDetails(interview));
        } else {
            // Other status changes keep the lighter recruiter-only notice.
            notificationService.interviewStatusChanged(interview.getRecruiterEmail(),
                    interview.getCandidate().getCandidateName(), next.name());
        }
        return toDto(interview);
    }

    /**
     * Panel feedback submission: saves the panel's assessment (ratings, comments, recommendation,
     * coding rounds) AND moves the interview to SUBMITTED in one atomic step, then emails the
     * recruiter a link to review it. Allowed for the assigned panelist, the owning recruiter, or an
     * admin; a CANCELLED or CLOSED interview can't accept feedback. The candidate is fixed here --
     * only the assessment content and the status change.
     */
    @Transactional
    public InterviewDTO submitFeedback(Long id, InterviewDTO dto) {
        Interview interview = findOrThrow(id);
        enforceCanAccess(interview);
        if (interview.getStatus() == InterviewStatus.CANCELLED || interview.getStatus() == InterviewStatus.CLOSED) {
            throw new BadRequestException("Feedback can't be submitted for a "
                    + interview.getStatus().name().toLowerCase() + " interview.");
        }
        applyFields(interview, dto);
        interview.setStatus(InterviewStatus.SUBMITTED);
        interview = interviewRepository.save(interview);
        auditService.record("Interview", interview.getInterviewId(), "FEEDBACK_SUBMITTED",
                "final rating=" + interview.getFinalRating());
        notificationService.interviewFeedbackSubmitted(emailDetails(interview));
        return toDto(interview);
    }

    @Transactional
    public void delete(Long id) {
        Interview interview = findOrThrow(id);
        enforceCanAccess(interview);
        releaseSlotIfAny(interview);
        auditService.record("Interview", interview.getInterviewId(), "DELETE",
                "candidate=" + interview.getCandidate().getCandidateName());
        interviewRepository.delete(interview);
    }

    /** Frees up the booked slot (if any) so it can be booked again -- used on cancel and delete. */
    private void releaseSlotIfAny(Interview interview) {
        InterviewSlot slot = interview.getSlot();
        if (slot != null && slot.getStatus() == SlotStatus.BOOKED) {
            slot.setStatus(SlotStatus.AVAILABLE);
            interviewSlotRepository.save(slot);
        }
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

    private InterviewLevel parseLevel(String raw) {
        try {
            return InterviewLevel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown interview level: " + raw);
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

    /**
     * RBAC record-ownership guard for a single interview, mirroring the row visibility in
     * InterviewSpecifications so a direct-by-id call (URL/API) can't bypass the list scoping:
     *   ADMIN     -> always allowed.
     *   PANEL     -> only the assigned interviewer (matched by login email against interviewer.email).
     *   RECRUITER -> only an interview they created (created_by) or are the assigned recruiter for
     *                (recruiter_email), matched case-insensitively against their login email.
     * AppUser.role is a single value, so exactly one branch applies per caller.
     */
    private void enforceCanAccess(Interview interview) {
        if (CurrentUser.hasRole("ADMIN")) {
            return;
        }
        String callerEmail = CurrentUser.emailOrSystem();
        if (CurrentUser.hasRole("PANEL")) {
            Interviewer interviewer = interview.getInterviewer();
            boolean owns = interviewer != null && interviewer.getEmail() != null
                    && interviewer.getEmail().equalsIgnoreCase(callerEmail);
            if (!owns) {
                throw new AccessDeniedException("You can only view or edit interviews assigned to you.");
            }
            return;
        }
        if (CurrentUser.hasRole("RECRUITER")) {
            boolean owns = (interview.getCreatedBy() != null && interview.getCreatedBy().equalsIgnoreCase(callerEmail))
                    || (interview.getRecruiterEmail() != null && interview.getRecruiterEmail().equalsIgnoreCase(callerEmail));
            if (!owns) {
                throw new AccessDeniedException("You can only view or edit assessments you created or are assigned to.");
            }
            return;
        }
        // Any other authenticated role has no access to individual assessment records.
        throw new AccessDeniedException("You do not have access to this assessment.");
    }

    private void applyFields(Interview interview, InterviewDTO dto) {
        interview.setPanelMemberName(dto.getPanelMemberName());
        interview.setRecruiterName(dto.getRecruiterName());
        interview.setRecruiterEmail(dto.getRecruiterEmail());
        interview.setPosition(dto.getPosition());
        if (StringUtils.hasText(dto.getLevelOfInterview())) {
            interview.setLevelOfInterview(InterviewLevel.valueOf(dto.getLevelOfInterview().toUpperCase()));
        }
        if (StringUtils.hasText(dto.getModeOfInterview())) {
            interview.setModeOfInterview(InterviewMode.valueOf(dto.getModeOfInterview().toUpperCase()));
        }
        interview.setMeetingLink(dto.getMeetingLink());
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
        dto.setRecruiterEmail(interview.getRecruiterEmail());
        if (interview.getInterviewer() != null) {
            dto.setInterviewerId(interview.getInterviewer().getInterviewerId());
            dto.setInterviewerName(interview.getInterviewer().getFullName());
        }
        if (interview.getSlot() != null) {
            dto.setSlotId(interview.getSlot().getSlotId());
            dto.setSlotCode(interview.getSlot().getSlotCode());
        }
        dto.setPosition(interview.getPosition());
        dto.setLevelOfInterview(interview.getLevelOfInterview() != null ? interview.getLevelOfInterview().name() : null);
        dto.setModeOfInterview(interview.getModeOfInterview() != null ? interview.getModeOfInterview().name() : null);
        dto.setMeetingLink(interview.getMeetingLink());
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
