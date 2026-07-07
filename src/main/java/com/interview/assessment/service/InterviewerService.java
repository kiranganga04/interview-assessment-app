package com.interview.assessment.service;

import com.interview.assessment.dto.InterviewerDTO;
import com.interview.assessment.entity.Interviewer;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.InterviewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** People Management: CRUD for the bookable interviewer directory (see Interviewer entity). */
@Service
@RequiredArgsConstructor
public class InterviewerService {

    private final InterviewerRepository interviewerRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<InterviewerDTO> listAll() {
        return interviewerRepository.findAll().stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(InterviewerDTO::getFullName))
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewerDTO get(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public InterviewerDTO create(InterviewerDTO dto) {
        if (interviewerRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new BadRequestException("An interviewer with this email already exists.");
        }
        Interviewer interviewer = new Interviewer();
        applyFields(interviewer, dto);
        interviewer = interviewerRepository.save(interviewer);
        auditService.record("Interviewer", interviewer.getInterviewerId(), "CREATE", interviewer.getFullName());
        return toDto(interviewer);
    }

    @Transactional
    public InterviewerDTO update(Long id, InterviewerDTO dto) {
        Interviewer interviewer = findOrThrow(id);
        if (!interviewer.getEmail().equalsIgnoreCase(dto.getEmail())
                && interviewerRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new BadRequestException("An interviewer with this email already exists.");
        }
        applyFields(interviewer, dto);
        interviewer = interviewerRepository.save(interviewer);
        auditService.record("Interviewer", interviewer.getInterviewerId(), "UPDATE", interviewer.getFullName());
        return toDto(interviewer);
    }

    @Transactional
    public void delete(Long id) {
        Interviewer interviewer = findOrThrow(id);
        auditService.record("Interviewer", interviewer.getInterviewerId(), "DELETE", interviewer.getFullName());
        interviewerRepository.delete(interviewer);
    }

    private Interviewer findOrThrow(Long id) {
        return interviewerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found: " + id));
    }

    private void applyFields(Interviewer interviewer, InterviewerDTO dto) {
        interviewer.setFullName(dto.getFullName());
        interviewer.setEmail(dto.getEmail().toLowerCase());
        interviewer.setContactNumber(dto.getContactNumber());
        interviewer.setAccount(dto.getAccount());
        interviewer.setGrade(dto.getGrade());
        interviewer.setLevelCapability(dto.getLevelCapability());
        interviewer.setSkillSet(dto.getSkillSet());
        if (dto.getActive() != null) {
            interviewer.setActive(dto.getActive());
        }
    }

    private InterviewerDTO toDto(Interviewer interviewer) {
        InterviewerDTO dto = new InterviewerDTO();
        dto.setInterviewerId(interviewer.getInterviewerId());
        dto.setFullName(interviewer.getFullName());
        dto.setEmail(interviewer.getEmail());
        dto.setContactNumber(interviewer.getContactNumber());
        dto.setAccount(interviewer.getAccount());
        dto.setGrade(interviewer.getGrade());
        dto.setLevelCapability(interviewer.getLevelCapability());
        dto.setSkillSet(interviewer.getSkillSet());
        dto.setActive(interviewer.isActive());
        return dto;
    }
}
