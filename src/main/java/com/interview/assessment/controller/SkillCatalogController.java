package com.interview.assessment.controller;

import com.interview.assessment.dto.SkillDTO;
import com.interview.assessment.service.SkillCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillCatalogController {

    private final SkillCatalogService skillCatalogService;

    /** Any authenticated user can read the active catalog — it feeds the assessment form dropdown. */
    @GetMapping
    public List<SkillDTO> listActive() {
        return skillCatalogService.listActive();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public List<SkillDTO> listAll() {
        return skillCatalogService.listAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public SkillDTO create(@Valid @RequestBody SkillDTO dto) {
        return skillCatalogService.create(dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public SkillDTO update(@PathVariable Long id, @Valid @RequestBody SkillDTO dto) {
        return skillCatalogService.update(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        skillCatalogService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
