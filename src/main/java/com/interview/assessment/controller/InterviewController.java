package com.interview.assessment.controller;

import com.interview.assessment.dto.InterviewDTO;
import com.interview.assessment.dto.PageResponse;
import com.interview.assessment.dto.ScheduleInterviewRequest;
import com.interview.assessment.dto.StatusChangeRequest;
import com.interview.assessment.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * Module 8: page/size/sort + level/status/search filters, all optional for backwards compatibility.
     * PANEL intentionally excluded: panel members submit/manage their own assessments (create + view a
     * specific record by id) but don't get to browse the full assessments list.
     */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @GetMapping
    public PageResponse<InterviewDTO> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "interviewId", direction = Sort.Direction.DESC) Pageable pageable) {
        return interviewService.search(level, status, search, pageable);
    }

    @GetMapping("/{id}")
    public InterviewDTO get(@PathVariable Long id) {
        return interviewService.get(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @PostMapping
    public InterviewDTO create(@Valid @RequestBody InterviewDTO dto) {
        return interviewService.create(dto);
    }

    /**
     * Interview Management: the Schedule Interview wizard books an available slot instead of
     * free-typing panel/date/time. ADMIN/RECRUITER only -- resourcing interviews against slots
     * is a scheduling concern, same scope as InterviewerController/InterviewSlotController.
     */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @PostMapping("/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewDTO schedule(@Valid @RequestBody ScheduleInterviewRequest request) {
        return interviewService.scheduleFromSlot(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @PutMapping("/{id}")
    public InterviewDTO update(@PathVariable Long id, @Valid @RequestBody InterviewDTO dto) {
        return interviewService.update(id, dto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @PatchMapping("/{id}/status")
    public InterviewDTO changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return interviewService.changeStatus(id, request.getStatus());
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interviewService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
