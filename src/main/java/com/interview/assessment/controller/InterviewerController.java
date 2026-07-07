package com.interview.assessment.controller;

import com.interview.assessment.dto.InterviewerDTO;
import com.interview.assessment.service.InterviewerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * People Management: admin/recruiter-only directory of bookable interviewers.
 * PANEL isn't included here -- this is a resourcing/scheduling concern for whoever
 * organizes interviews, not something a panel member needs to browse or edit.
 */
@RestController
@RequestMapping("/api/interviewers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
public class InterviewerController {

    private final InterviewerService interviewerService;

    @GetMapping
    public List<InterviewerDTO> list() {
        return interviewerService.listAll();
    }

    @GetMapping("/{id}")
    public InterviewerDTO get(@PathVariable Long id) {
        return interviewerService.get(id);
    }

    @PostMapping
    public InterviewerDTO create(@Valid @RequestBody InterviewerDTO dto) {
        return interviewerService.create(dto);
    }

    @PutMapping("/{id}")
    public InterviewerDTO update(@PathVariable Long id, @Valid @RequestBody InterviewerDTO dto) {
        return interviewerService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interviewerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
