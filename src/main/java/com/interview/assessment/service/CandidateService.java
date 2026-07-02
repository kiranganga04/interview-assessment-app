package com.interview.assessment.service;

import com.interview.assessment.dto.CandidateDTO;
import com.interview.assessment.entity.Candidate;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateService {

    private final CandidateRepository candidateRepository;

    public List<CandidateDTO> getAll() {
        return candidateRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<CandidateDTO> search(String name) {
        return candidateRepository.findByCandidateNameContainingIgnoreCase(name).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CandidateDTO getById(Long id) {
        return toDto(findEntity(id));
    }

    public CandidateDTO create(CandidateDTO dto) {
        Candidate candidate = Candidate.builder()
                .candidateName(dto.getCandidateName())
                .mobileNumber(dto.getMobileNumber())
                .overallExperience(dto.getOverallExperience())
                .currentRole(dto.getCurrentRole())
                .build();
        return toDto(candidateRepository.save(candidate));
    }

    public CandidateDTO update(Long id, CandidateDTO dto) {
        Candidate candidate = findEntity(id);
        candidate.setCandidateName(dto.getCandidateName());
        candidate.setMobileNumber(dto.getMobileNumber());
        candidate.setOverallExperience(dto.getOverallExperience());
        candidate.setCurrentRole(dto.getCurrentRole());
        return toDto(candidateRepository.save(candidate));
    }

    public void delete(Long id) {
        Candidate candidate = findEntity(id);
        candidateRepository.delete(candidate);
    }

    private Candidate findEntity(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id: " + id));
    }

    private CandidateDTO toDto(Candidate c) {
        return CandidateDTO.builder()
                .candidateId(c.getCandidateId())
                .candidateName(c.getCandidateName())
                .mobileNumber(c.getMobileNumber())
                .overallExperience(c.getOverallExperience())
                .currentRole(c.getCurrentRole())
                .build();
    }
}
