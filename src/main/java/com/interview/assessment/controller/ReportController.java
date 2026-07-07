package com.interview.assessment.controller;

import com.interview.assessment.dto.*;
import com.interview.assessment.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Module 7: reporting & analytics used by the dashboard landing page. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public DashboardSummaryDTO summary() {
        return reportService.dashboardSummary();
    }

    @GetMapping("/pass-rate")
    public List<PassRateReportDTO> passRate() {
        return reportService.passRateByLevel();
    }

    @GetMapping("/skill-averages")
    public List<SkillAverageDTO> skillAverages() {
        return reportService.skillAverages();
    }

    @GetMapping("/panelist-calibration")
    public List<PanelistCalibrationDTO> panelistCalibration() {
        return reportService.panelistCalibration();
    }

    /** Dashboard Overview "Monthly Interviews" chart. Defaults to the last 6 calendar months. */
    @GetMapping("/monthly-interviews")
    public List<MonthlyInterviewCountDTO> monthlyInterviews(@RequestParam(defaultValue = "6") int months) {
        return reportService.monthlyInterviews(months);
    }

    /** Dashboard Overview "Today's Agenda". */
    @GetMapping("/today-agenda")
    public List<AgendaItemDTO> todaysAgenda() {
        return reportService.todaysAgenda();
    }
}
