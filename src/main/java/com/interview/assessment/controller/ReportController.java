package com.interview.assessment.controller;

import com.interview.assessment.dto.*;
import com.interview.assessment.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Module 7: reporting & analytics.
 *
 * Access split: the Dashboard Overview endpoints (summary / today-agenda / monthly-interviews)
 * are open to ADMIN, RECRUITER and PANEL, because every one of them aggregates over
 * ReportService.scopedInterviews() -- so a PANEL user sees a Dashboard built only from the
 * interviews assigned to them, a RECRUITER only their own, and an ADMIN everything. The deeper
 * Analytics endpoints (pass-rate / skill-averages / panelist-calibration) stay ADMIN/RECRUITER
 * only, since those are cross-panel comparisons rather than a personal overview.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @GetMapping("/summary")
    public DashboardSummaryDTO summary() {
        return reportService.dashboardSummary();
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @GetMapping("/pass-rate")
    public List<PassRateReportDTO> passRate() {
        return reportService.passRateByLevel();
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @GetMapping("/skill-averages")
    public List<SkillAverageDTO> skillAverages() {
        return reportService.skillAverages();
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
    @GetMapping("/panelist-calibration")
    public List<PanelistCalibrationDTO> panelistCalibration() {
        return reportService.panelistCalibration();
    }

    /** Dashboard Overview "Monthly Interviews" chart. Defaults to the last 6 calendar months. */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @GetMapping("/monthly-interviews")
    public List<MonthlyInterviewCountDTO> monthlyInterviews(@RequestParam(defaultValue = "6") int months) {
        return reportService.monthlyInterviews(months);
    }

    /** Dashboard Overview "Today's Agenda". */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @GetMapping("/today-agenda")
    public List<AgendaItemDTO> todaysAgenda() {
        return reportService.todaysAgenda();
    }

    /**
     * Admin Dashboard "Recruiter Workload": interviews owned per recruiter. ADMIN only — this is a
     * cross-recruiter oversight view, not a personal one, so unlike the Dashboard Overview endpoints
     * it is not opened to RECRUITER/PANEL.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/recruiter-workload")
    public List<RecruiterWorkloadDTO> recruiterWorkload() {
        return reportService.recruiterWorkload();
    }

    /** Admin Dashboard "Data Hygiene": orphaned interviews, unlinked interviewers, skill-catalog size. ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/data-hygiene")
    public DataHygieneDTO dataHygiene() {
        return reportService.dataHygiene();
    }

    /**
     * Downloadable interview list (CSV) scoped to the caller -- a RECRUITER's own, a PANEL's own,
     * an ADMIN's everything. Optional ?month=YYYY-MM limits it to that calendar month by scheduled_at.
     * Includes a Category column (Taken / Cancelled / Others).
     */
    @PreAuthorize("hasAnyRole('ADMIN','RECRUITER','PANEL')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String month) {
        byte[] body = reportService.interviewsCsv(month).getBytes(StandardCharsets.UTF_8);
        String filename = "interviews" + (month != null && !month.isBlank() ? "-" + month : "") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
