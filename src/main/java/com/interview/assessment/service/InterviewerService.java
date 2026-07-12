package com.interview.assessment.service;

import com.interview.assessment.dto.InterviewerDTO;
import com.interview.assessment.dto.PageResponse;
import com.interview.assessment.entity.Interviewer;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.InterviewerRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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

    /**
     * People Management: page/size/sort + search/status filters for the Interviewers directory
     * table, mirroring InterviewService.search()'s Specification + Pageable pattern (module 8).
     * Deliberately a separate endpoint from listAll() rather than a replacement -- listAll()
     * still backs the Interviewer dropdowns (Add Slot form, Teams view) which need the whole
     * directory in one call, not one page of it.
     */
    @Transactional(readOnly = true)
    public PageResponse<InterviewerDTO> search(String search, String status, Pageable pageable) {
        Specification<Interviewer> spec = buildSpecification(search, status);
        Page<Interviewer> page = interviewerRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    private Specification<Interviewer> buildSpecification(String search, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("active"), "ACTIVE".equalsIgnoreCase(status)));
            }
            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("account"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("skillSet"), "")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
