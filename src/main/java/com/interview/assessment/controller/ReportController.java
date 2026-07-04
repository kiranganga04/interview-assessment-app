package com.interview.assessment.controller;

import com.interview.assessment.dto.*;
import com.interview.assessment.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
