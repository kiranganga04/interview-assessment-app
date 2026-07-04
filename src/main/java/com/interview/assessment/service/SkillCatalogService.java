package com.interview.assessment.service;

import com.interview.assessment.dto.SkillDTO;
import com.interview.assessment.entity.Skill;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Module 4: admin-managed skill catalog backing the assessment form's skill dropdown. */
@Service
@RequiredArgsConstructor
public class SkillCatalogService {

    private final SkillRepository skillRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SkillDTO> listActive() {
        return skillRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SkillDTO> listAll() {
        return skillRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public SkillDTO create(SkillDTO dto) {
        if (skillRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Skill '" + dto.getName() + "' already exists.");
        }
        Skill skill = new Skill();
        applyFields(skill, dto);
        skill = skillRepository.save(skill);
        auditService.record("Skill", skill.getSkillId(), "CREATE", skill.getName());
        return toDto(skill);
    }

    @Transactional
    public SkillDTO update(Long id, SkillDTO dto) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found: " + id));
        applyFields(skill, dto);
        skill = skillRepository.save(skill);
        auditService.record("Skill", skill.getSkillId(), "UPDATE", skill.getName());
        return toDto(skill);
    }

    @Transactional
    public void delete(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found: " + id));
        // Soft-delete: assessments already recorded against this skill keep their history.
        skill.setActive(false);
        skillRepository.save(skill);
        auditService.record("Skill", skill.getSkillId(), "DELETE", skill.getName());
    }

    private void applyFields(Skill skill, SkillDTO dto) {
        skill.setName(dto.getName());
        skill.setApplicableLevels(dto.getApplicableLevels());
        skill.setActive(dto.isActive());
    }

    private SkillDTO toDto(Skill skill) {
        SkillDTO dto = new SkillDTO();
        dto.setSkillId(skill.getSkillId());
        dto.setName(skill.getName());
        dto.setApplicableLevels(skill.getApplicableLevels());
        dto.setActive(skill.isActive());
        return dto;
    }
}
