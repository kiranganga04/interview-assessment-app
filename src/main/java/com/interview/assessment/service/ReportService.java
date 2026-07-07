package com.interview.assessment.service;

import com.interview.assessment.dto.*;
import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.InterviewStatus;
import com.interview.assessment.entity.PanelType;
import com.interview.assessment.repository.CandidateRepository;
import com.interview.assessment.repository.InterviewRepository;
import com.interview.assessment.repository.InterviewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    private static final DateTimeFormatter MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final InterviewRepository interviewRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewerRepository interviewerRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDTO dashboardSummary() {
        List<Interview> all = interviewRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        long scheduled = all.stream().filter(i -> i.getStatus() == InterviewStatus.SCHEDULED).count();
        long submitted = all.stream().filter(i -> i.getStatus() == InterviewStatus.SUBMITTED).count();
        long recommended = all.stream().filter(i -> i.getStatus() == InterviewStatus.RECOMMENDED).count();
        long closed = all.stream().filter(i -> i.getStatus() == InterviewStatus.CLOSED).count();
        long cancelled = all.stream().filter(i -> i.getStatus() == InterviewStatus.CANCELLED).count();
        long overdue = all.stream()
                .filter(i -> i.getStatus() == InterviewStatus.SCHEDULED
                        && i.getScheduledAt() != null && i.getScheduledAt().isBefore(now))
                .count();
        long today = all.stream()
                .filter(i -> i.getScheduledAt() != null && i.getScheduledAt().toLocalDate().isEqual(now.toLocalDate()))
                .count();
        double avgFinal = average(all.stream().map(Interview::getFinalRating));

        DashboardSummaryDTO dto = new DashboardSummaryDTO();
        dto.setTotalInterviews(all.size());
        dto.setScheduledCount(scheduled);
        dto.setSubmittedCount(submitted);
        dto.setRecommendedCount(recommended);
        dto.setAverageFinalRating(avgFinal);
        dto.setClosedCount(closed);
        dto.setCancelledCount(cancelled);
        dto.setCompletedCount(recommended + closed);
        dto.setCandidateCount(candidateRepository.count());
        dto.setInterviewerCount(interviewerRepository.count());
        dto.setTodaysInterviewCount(today);
        dto.setPendingFeedbackCount(submitted);
        dto.setOverdueCount(overdue);
        return dto;
    }

    /** Bar-chart data for the last `months` calendar months (oldest first), keyed by scheduled_at falling in that month. */
    @Transactional(readOnly = true)
    public List<MonthlyInterviewCountDTO> monthlyInterviews(int months) {
        YearMonth current = YearMonth.from(LocalDate.now());
        Map<YearMonth, Long> byMonth = new TreeMap<>();
        for (int i = months - 1; i >= 0; i--) {
            byMonth.put(current.minusMonths(i), 0L);
        }
        interviewRepository.findAll().stream()
                .filter(i -> i.getScheduledAt() != null)
                .map(i -> YearMonth.from(i.getScheduledAt()))
                .filter(byMonth::containsKey)
                .forEach(ym -> byMonth.merge(ym, 1L, Long::sum));

        return byMonth.entrySet().stream()
                .map(e -> new MonthlyInterviewCountDTO(e.getKey().format(MONTH_KEY_FORMAT), e.getValue()))
                .toList();
    }

    /** Today's Agenda: interviews scheduled for today, earliest first. */
    @Transactional(readOnly = true)
    public List<AgendaItemDTO> todaysAgenda() {
        LocalDate today = LocalDate.now();
        return interviewRepository.findAll().stream()
                .filter(i -> i.getScheduledAt() != null && i.getScheduledAt().toLocalDate().isEqual(today))
                .sorted(Comparator.comparing(Interview::getScheduledAt))
                .map(i -> {
                    AgendaItemDTO item = new AgendaItemDTO();
                    item.setInterviewId(i.getInterviewId());
                    item.setCandidateName(i.getCandidate() != null ? i.getCandidate().getCandidateName() : null);
                    item.setLevelOfInterview(i.getLevelOfInterview() != null ? i.getLevelOfInterview().name() : null);
                    item.setStatus(i.getStatus() != null ? i.getStatus().name() : null);
                    item.setScheduledAt(i.getScheduledAt());
                    item.setInterviewerOrPanelName(i.getInterviewer() != null ? i.getInterviewer().getFullName() : i.getPanelMemberName());
                    item.setModeOfInterview(i.getModeOfInterview() != null ? i.getModeOfInterview().name() : null);
                    return item;
                })
                .toList();
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
