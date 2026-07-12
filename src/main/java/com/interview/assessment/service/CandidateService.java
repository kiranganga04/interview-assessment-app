package com.interview.assessment.service;

import com.interview.assessment.dto.CandidateDTO;
import com.interview.assessment.dto.PageResponse;
import com.interview.assessment.entity.Candidate;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.CandidateRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Candidates are returned as a plain list (not paginated) because the primary consumer
 * is the candidate picker dropdown on the assessment form — the paginated, filterable
 * table experience (module 8) lives on the interview list, which is where records
 * actually grow unbounded.
 */
@Service
@RequiredArgsConstructor
public class CandidateService {

    private static final int MAX_RESULTS = 500;

    private final CandidateRepository candidateRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CandidateDTO> search(String name) {
        var pageable = PageRequest.of(0, MAX_RESULTS, Sort.by("candidateName").ascending());
        var page = StringUtils.hasText(name)
                ? candidateRepository.findByCandidateNameContainingIgnoreCase(name, pageable)
                : candidateRepository.findAll(pageable);
        return page.map(this::toDto).getContent();
    }

    /**
     * People Management: page/size/sort + search/email filters for the Candidates directory
     * table, mirroring InterviewService.search()'s Specification + Pageable pattern (module 8).
     * Deliberately separate from search(name) above -- that method backs the candidate picker
     * dropdown (assessment form / Schedule Interview wizard), which needs a plain unpaginated
     * list capped at MAX_RESULTS, not one page of the directory.
     */
    @Transactional(readOnly = true)
    public PageResponse<CandidateDTO> searchPaged(String search, String emailFilter, Pageable pageable) {
        Specification<Candidate> spec = buildSpecification(search, emailFilter);
        Page<Candidate> page = candidateRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    private Specification<Candidate> buildSpecification(String search, String emailFilter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(search)) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("candidateName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("mobileNumber"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("currentRole"), "")), like)));
            }
            if ("HAS_EMAIL".equalsIgnoreCase(emailFilter)) {
                predicates.add(cb.and(cb.isNotNull(root.get("email")), cb.notEqual(root.get("email"), "")));
            } else if ("NO_EMAIL".equalsIgnoreCase(emailFilter)) {
                predicates.add(cb.or(cb.isNull(root.get("email")), cb.equal(root.get("email"), "")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public CandidateDTO get(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public CandidateDTO create(CandidateDTO dto) {
        Candidate candidate = new Candidate();
        applyFields(candidate, dto);
        candidate = candidateRepository.save(candidate);
        auditService.record("Candidate", candidate.getCandidateId(), "CREATE", candidate.getCandidateName());
        return toDto(candidate);
    }

    @Transactional
    public CandidateDTO update(Long id, CandidateDTO dto) {
        Candidate candidate = findOrThrow(id);
        applyFields(candidate, dto);
        candidate = candidateRepository.save(candidate);
        auditService.record("Candidate", candidate.getCandidateId(), "UPDATE", candidate.getCandidateName());
        return toDto(candidate);
    }

    @Transactional
    public void delete(Long id) {
        Candidate candidate = findOrThrow(id);
        auditService.record("Candidate", candidate.getCandidateId(), "DELETE", candidate.getCandidateName());
        candidateRepository.delete(candidate);
    }

    private Candidate findOrThrow(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
    }

    private void applyFields(Candidate candidate, CandidateDTO dto) {
        candidate.setCandidateName(dto.getCandidateName());
        candidate.setEmail(dto.getEmail());
        candidate.setMobileNumber(dto.getMobileNumber());
        candidate.setOverallExperience(dto.getOverallExperience());
        candidate.setCurrentRole(dto.getCurrentRole());
    }

    private CandidateDTO toDto(Candidate candidate) {
        CandidateDTO dto = new CandidateDTO();
        dto.setCandidateId(candidate.getCandidateId());
        dto.setCandidateName(candidate.getCandidateName());
        dto.setEmail(candidate.getEmail());
        dto.setMobileNumber(candidate.getMobileNumber());
        dto.setOverallExperience(candidate.getOverallExperience());
        dto.setCurrentRole(candidate.getCurrentRole());
        return dto;
    }
}
