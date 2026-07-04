package com.interview.assessment.service;

import com.interview.assessment.dto.*;
import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.InterviewStatus;
import com.interview.assessment.entity.PanelType;
import com.interview.assessment.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Module 7 (reporting & analytics). Aggregation is done in-memory over InterviewRepository's
 * result set, which is the right trade-off for the data volumes this internal tool deals with
 * (hundreds to low thousands of assessments); if that ever changes, these three methods are
 * the place to move to native aggregate SQL.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final InterviewRepository interviewRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDTO dashboardSummary() {
        List<Interview> all = interviewRepository.findAll();
        long scheduled = all.stream().filter(i -> i.getStatus() == InterviewStatus.SCHEDULED).count();
        long submitted = all.stream().filter(i -> i.getStatus() == InterviewStatus.SUBMITTED).count();
        long recommended = all.stream().filter(i -> i.getStatus() == InterviewStatus.RECOMMENDED).count();
        double avgFinal = average(all.stream().map(Interview::getFinalRating));
        return new DashboardSummaryDTO(all.size(), scheduled, submitted, recommended, avgFinal);
    }

    @Transactional(readOnly = true)
    public List<PassRateReportDTO> passRateByLevel() {
        Map<String, List<Interview>> byLevel = interviewRepository.findAll().stream()
                .filter(i -> i.getLevelOfInterview() != null)
                .collect(Collectors.groupingBy(i -> i.getLevelOfInterview().name()));

        return byLevel.entrySet().stream()
                .map(entry -> {
                    long total = entry.getValue().size();
                    long recommended = entry.getValue().stream()
                            .filter(i -> i.getStatus() == InterviewStatus.RECOMMENDED
                                    || (i.getPanelRecommendation() != null && !i.getPanelRecommendation().isBlank()))
                            .count();
                    double rate = total == 0 ? 0 : (recommended * 100.0) / total;
                    return new PassRateReportDTO(entry.getKey(), total, recommended, round1(rate));
                })
                .sorted(Comparator.comparing(PassRateReportDTO::getLevel))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SkillAverageDTO> skillAverages() {
        return interviewRepository.findAll().stream()
                .flatMap(i -> i.getSkillAssessments().stream())
                .filter(sa -> sa.getPanelType() == PanelType.INTERNAL && sa.getRating() != null && sa.getSkillName() != null)
                .collect(Collectors.groupingBy(sa -> sa.getSkillName()))
                .entrySet().stream()
                .map(entry -> {
                    double avg = average(entry.getValue().stream().map(sa -> sa.getRating()));
                    return new SkillAverageDTO(entry.getKey(), round1(avg), entry.getValue().size());
                })
                .sorted(Comparator.comparing(SkillAverageDTO::getSkillName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PanelistCalibrationDTO> panelistCalibration() {
        List<Interview> all = interviewRepository.findAll();
        double overallAverage = average(all.stream().map(Interview::getFinalRating));

        Map<String, List<Interview>> byPanelist = all.stream()
                .filter(i -> i.getPanelMemberName() != null && !i.getPanelMemberName().isBlank())
                .collect(Collectors.groupingBy(Interview::getPanelMemberName));

        return byPanelist.entrySet().stream()
                .map(entry -> {
                    double avg = average(entry.getValue().stream().map(Interview::getFinalRating));
                    return new PanelistCalibrationDTO(entry.getKey(), entry.getValue().size(), round1(avg), round1(avg - overallAverage));
                })
                .sorted(Comparator.comparing(PanelistCalibrationDTO::getPanelMemberName))
                .toList();
    }

    private double average(java.util.stream.Stream<BigDecimal> values) {
        List<BigDecimal> list = values.filter(java.util.Objects::nonNull).toList();
        if (list.isEmpty()) {
            return 0.0;
        }
        double sum = list.stream().mapToDouble(BigDecimal::doubleValue).sum();
        return sum / list.size();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
