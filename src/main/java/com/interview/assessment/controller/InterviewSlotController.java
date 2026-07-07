package com.interview.assessment.controller;

import com.interview.assessment.dto.InterviewSlotDTO;
import com.interview.assessment.service.InterviewSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Interview Management: admin/recruiter-only availability windows that the Schedule
 * Interview wizard books against. Same role scope as InterviewerController -- resourcing
 * interviews is a recruiter/admin concern, not something PANEL browses or edits.
 */
@RestController
@RequestMapping("/api/interview-slots")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
public class InterviewSlotController {

    private final InterviewSlotService interviewSlotService;

    @GetMapping
    public List<InterviewSlotDTO> list(
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        if (Boolean.TRUE.equals(availableOnly)) {
            return interviewSlotService.listAvailableFrom(from != null ? from : LocalDate.now());
        }
        return interviewSlotService.listAll();
    }

    @GetMapping("/{id}")
    public InterviewSlotDTO get(@PathVariable Long id) {
        return interviewSlotService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewSlotDTO create(@Valid @RequestBody InterviewSlotDTO dto) {
        return interviewSlotService.create(dto);
    }

    @PutMapping("/{id}")
    public InterviewSlotDTO update(@PathVariable Long id, @Valid @RequestBody InterviewSlotDTO dto) {
        return interviewSlotService.update(id, dto);
    }

    /** Withdraws an AVAILABLE slot (not a delete -- keeps the row/audit trail; booked slots can't be cancelled here, only via cancelling the interview). */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        interviewSlotService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
