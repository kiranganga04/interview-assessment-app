package com.interview.assessment.controller;

import com.interview.assessment.dto.CandidateDTO;
import com.interview.assessment.service.CandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    @GetMapping
    public ResponseEntity<List<CandidateDTO>> getAll(
            @RequestParam(required = false) String name) {
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(candidateService.search(name));
        }
        return ResponseEntity.ok(candidateService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CandidateDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CandidateDTO> create(@Valid @RequestBody CandidateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(candidateService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CandidateDTO> update(@PathVariable Long id, @Valid @RequestBody CandidateDTO dto) {
        return ResponseEntity.ok(candidateService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        candidateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
