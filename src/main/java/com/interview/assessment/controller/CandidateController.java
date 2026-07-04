package com.interview.assessment.controller;

import com.interview.assessment.dto.CandidateDTO;
import com.interview.assessment.service.CandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    @GetMapping
    public List<CandidateDTO> list(@RequestParam(required = false) String name) {
        return candidateService.search(name);
    }

    @GetMapping("/{id}")
    public CandidateDTO get(@PathVariable Long id) {
        return candidateService.get(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @PostMapping
    public CandidateDTO create(@Valid @RequestBody CandidateDTO dto) {
        return candidateService.create(dto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @PutMapping("/{id}")
    public CandidateDTO update(@PathVariable Long id, @Valid @RequestBody CandidateDTO dto) {
        return candidateService.update(id, dto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        candidateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
