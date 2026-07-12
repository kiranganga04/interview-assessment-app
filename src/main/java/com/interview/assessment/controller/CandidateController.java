package com.interview.assessment.controller;

import com.interview.assessment.dto.CandidateDTO;
import com.interview.assessment.dto.PageResponse;
import com.interview.assessment.service.CandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    /**
     * People Management: page/size/sort + search/email filters, backing the Candidates
     * directory table's pagination. Separate from list() above, which stays a plain,
     * unpaginated (capped) list for the candidate picker dropdown (assessment form /
     * Schedule Interview wizard).
     */
    @GetMapping("/search")
    public PageResponse<CandidateDTO> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String emailFilter,
            @PageableDefault(size = 10, sort = "candidateName", direction = Sort.Direction.ASC) Pageable pageable) {
        return candidateService.searchPaged(search, emailFilter, pageable);
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
