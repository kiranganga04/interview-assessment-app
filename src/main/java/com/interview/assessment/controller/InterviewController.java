package com.interview.assessment.controller;

import com.interview.assessment.dto.InterviewDTO;
import com.interview.assessment.dto.PageResponse;
import com.interview.assessment.dto.RescheduleRequest;
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
     * specific record by id) but don't get to browse the full assessments list. For ADMIN the list is
     * unrestricted; for RECRUITER it is scoped server-side to the records they own (see
     * InterviewService.search / InterviewSpecifications).
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

    /**
     * Feedback & Reports: a Panel member's own "to-do list" -- only interviews assigned to them
     * (matched by login email against the Interviewers directory) that are still awaiting
     * feedback. Backs the "My Interviews" page, which replaces free-form candidate selection
     * for Panel logins.
     */
    @PreAuthorize("hasRole('PANEL')")
    @GetMapping("/mine")
    public java.util.List<InterviewDTO> mine() {
        return interviewService.myOpenInterviews();
    }

    /**
     * Feedback & Reports: a Panel member's full interview history -- every interview assigned to
     * them as the interviewer, any status, most recent first. Backs the "My Interview History"
     * page. PANEL-only, and always scoped to the caller inside the service, so a panel member can
     * only ever see their own history.
     */
    @PreAuthorize("hasRole('PANEL')")
    @GetMapping("/mine/history")
    public java.util.List<InterviewDTO> myHistory() {
        return interviewService.myInterviewHistory();
    }

    /**
     * ADMIN/RECRUITER only -- Panel members used to be able to free-create an assessment record
     * for any candidate via this endpoint, which is exactly the loophole the "My Interviews" /
     * candidate-restriction change closes. Panel now only ever submits feedback by editing an
     * interview that's already assigned to them (see update()/changeStatus() ownership checks
     * in InterviewService), never by creating a brand new one.
     */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
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

    /**
     * Interview lifecycle (IN_PROGRESS / SUBMITTED / RECOMMENDED / CLOSED / CANCELLED) is a
     * recruiter/admin responsibility. PANEL is intentionally excluded: a panelist advances an
     * interview only by submitting their feedback (see /submit-feedback), never by driving the
     * status directly, and cannot close or cancel a record.
     */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @PatchMapping("/{id}/status")
    public InterviewDTO changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return interviewService.changeStatus(id, request.getStatus());
    }

    /**
     * Explicit reschedule: move an interview to a new date/time (and optionally update the meeting
     * link). Recruiter/admin only -- rescheduling is a coordination concern (slots, candidate
     * availability), not a panelist action. Emails all parties the new time and records a
     * RESCHEDULE audit entry.
     */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @PatchMapping("/{id}/reschedule")
    public InterviewDTO reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
        return interviewService.reschedule(id, request);
    }

    /**
     * Panel feedback submission: saves the assessment and moves the interview to SUBMITTED in one
     * call, then emails the recruiter a link to review it. Assigned panelist / owning recruiter / admin.
     */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @PostMapping("/{id}/submit-feedback")
    public InterviewDTO submitFeedback(@PathVariable Long id, @Valid @RequestBody InterviewDTO dto) {
        return interviewService.submitFeedback(id, dto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interviewService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
