package com.interview.assessment.controller;

import com.interview.assessment.dto.InterviewDTO;
import com.interview.assessment.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping
    public ResponseEntity<List<InterviewDTO>> getAll(
            @RequestParam(required = false) Long candidateId,
            @RequestParam(required = false) String level) {
        if (candidateId != null) {
            return ResponseEntity.ok(interviewService.getByCandidate(candidateId));
        }
        if (level != null && !level.isBlank()) {
            return ResponseEntity.ok(interviewService.getByLevel(level));
        }
        return ResponseEntity.ok(interviewService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(interviewService.getById(id));
    }

    @PostMapping
    public ResponseEntity<InterviewDTO> create(@Valid @RequestBody InterviewDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewDTO> update(@PathVariable Long id, @Valid @RequestBody InterviewDTO dto) {
        return ResponseEntity.ok(interviewService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interviewService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
