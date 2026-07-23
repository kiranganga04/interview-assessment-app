package com.interview.assessment.service;

import com.interview.assessment.dto.*;
import com.interview.assessment.entity.AppUser;
import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.InterviewStatus;
import com.interview.assessment.entity.PanelType;
import com.interview.assessment.repository.AppUserRepository;
import com.interview.assessment.repository.CandidateRepository;
import com.interview.assessment.repository.InterviewRepository;
import com.interview.assessment.repository.InterviewSpecifications;
import com.interview.assessment.repository.InterviewerRepository;
import com.interview.assessment.repository.SkillRepository;
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
 * (hundreds to low thousands of assessments); if that ever changes, these methods are
 * the place to move to native aggregate SQL.
 *
 * RBAC: every aggregation runs over scopedInterviews() rather than findAll(), so a RECRUITER's
 * Dashboard Overview and Analytics only ever count their own assessment records (created or
 * assigned), while an ADMIN still sees the whole organisation. See InterviewSpecifications.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final InterviewRepository interviewRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewerRepository interviewerRepository;
    private final AppUserRepository appUserRepository;
    private final SkillRepository skillRepository;

    /**
     * The one place row-visibility is applied for reporting: ADMIN -> all interviews;
     * RECRUITER -> only their own (created_by / recruiter_email); PANEL -> only interviews they're
     * the assigned interviewer on (PANEL can't currently reach the reports controller, but keeping
     * the scoping here means it stays correct if that ever changes).
     */
    private List<Interview> scopedInterviews() {
        return interviewRepository.findAll(InterviewSpecifications.visibleToCurrentUser());
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDTO dashboardSummary() {
        List<Interview> all = scopedInterviews();
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
        // Candidate / interviewer tiles are directory sizes ("in the system" / "in the directory"),
        // not assessment records, so they stay global for every role -- only the interview-derived
        // metrics above are scoped to the caller.
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
        scopedInterviews().stream()
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
        return scopedInterviews().stream()
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
        Map<String, List<Interview>> byLevel = scopedInterviews().stream()
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
        return scopedInterviews().stream()
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
        List<Interview> all = scopedInterviews();
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

    // ---- Admin dashboard (governance) — ADMIN-only endpoints. --------------------------------
    // These run over scopedInterviews() like everything else; because the caller is always ADMIN
    // (enforced in ReportController), that scope is the whole organisation, and it deliberately
    // includes the legacy rows whose created_by / recruiter_email are null (admin-only-visible),
    // which is exactly what the orphaned-row count needs to see.

    /**
     * Interviews owned per recruiter, most-loaded first. "Owner" mirrors the RBAC ownership key
     * (recruiter_email if present, else the auditing-stamped created_by). Interviews with neither
     * are unattributable and are excluded here (they surface separately in the data-hygiene card).
     */
    @Transactional(readOnly = true)
    public List<RecruiterWorkloadDTO> recruiterWorkload() {
        Map<String, List<Interview>> byOwner = scopedInterviews().stream()
                .filter(i -> ownerKey(i) != null)
                .collect(Collectors.groupingBy(this::ownerKey));

        return byOwner.entrySet().stream()
                .map(entry -> {
                    List<Interview> list = entry.getValue();
                    long active = list.stream().filter(i -> isInFlight(i.getStatus())).count();
                    return new RecruiterWorkloadDTO(entry.getKey(), displayName(entry.getKey(), list), active, list.size());
                })
                .sorted(Comparator.comparingLong(RecruiterWorkloadDTO::getTotalCount).reversed()
                        .thenComparing(RecruiterWorkloadDTO::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Data-quality signals for the admin dashboard: orphaned rows, unlinked interviewers, catalog size. */
    @Transactional(readOnly = true)
    public DataHygieneDTO dataHygiene() {
        long orphaned = scopedInterviews().stream()
                .filter(i -> isBlank(i.getCreatedBy()) && isBlank(i.getRecruiterEmail()))
                .count();

        long interviewersWithoutUser = interviewerRepository.findAll().stream()
                .filter(iv -> !isBlank(iv.getEmail()))
                .filter(iv -> !appUserRepository.existsByEmailIgnoreCase(iv.getEmail()))
                .count();

        long activeSkills = skillRepository.findByActiveTrueOrderByNameAsc().size();

        return new DataHygieneDTO(orphaned, interviewersWithoutUser, activeSkills);
    }

    /** RBAC ownership key for workload grouping: recruiter_email, else created_by, else null (unattributable). */
    private String ownerKey(Interview i) {
        if (!isBlank(i.getRecruiterEmail())) {
            return i.getRecruiterEmail().toLowerCase();
        }
        if (!isBlank(i.getCreatedBy())) {
            return i.getCreatedBy().toLowerCase();
        }
        return null;
    }

    /** In-flight = not yet a terminal outcome; used for the "active" workload count. */
    private boolean isInFlight(InterviewStatus status) {
        return status == InterviewStatus.SCHEDULED
                || status == InterviewStatus.IN_PROGRESS
                || status == InterviewStatus.SUBMITTED;
    }

    /** Prefer an AppUser full name, fall back to the interview's free-text recruiter_name, else the email. */
    private String displayName(String email, List<Interview> owned) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .map(AppUser::getFullName)
                .filter(name -> !isBlank(name))
                .orElseGet(() -> owned.stream()
                        .map(Interview::getRecruiterName)
                        .filter(name -> !isBlank(name))
                        .findFirst()
                        .orElse(email));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * CSV export of the caller's scoped interviews (recruiter -> own, panel -> own, admin -> all),
     * optionally limited to a single YYYY-MM month by scheduled_at. Includes a business "Category"
     * column (Taken / Cancelled / Others) so the export doubles as the monthly interview list.
     */
    @Transactional(readOnly = true)
    public String interviewsCsv(String month) {
        final YearMonth ym = parseMonth(month);
        StringBuilder sb = new StringBuilder();
        sb.append("Interview ID,Candidate,Position,Level,Mode,Panel/Interviewer,Recruiter,")
          .append("Scheduled At,Interview Date,Status,Category,Final Rating,Recommendation\n");
        scopedInterviews().stream()
                .filter(i -> ym == null || (i.getScheduledAt() != null && YearMonth.from(i.getScheduledAt()).equals(ym)))
                .sorted(Comparator.comparing(Interview::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(i -> sb
                        .append(csv(i.getInterviewId())).append(',')
                        .append(csv(i.getCandidate() != null ? i.getCandidate().getCandidateName() : "")).append(',')
                        .append(csv(i.getPosition())).append(',')
                        .append(csv(i.getLevelOfInterview() != null ? i.getLevelOfInterview().name() : "")).append(',')
                        .append(csv(i.getModeOfInterview() != null ? i.getModeOfInterview().name() : "")).append(',')
                        .append(csv(i.getInterviewer() != null ? i.getInterviewer().getFullName() : i.getPanelMemberName())).append(',')
                        .append(csv(i.getRecruiterName())).append(',')
                        .append(csv(i.getScheduledAt() != null ? i.getScheduledAt().toString() : "")).append(',')
                        .append(csv(i.getInterviewDate() != null ? i.getInterviewDate().toString() : "")).append(',')
                        .append(csv(i.getStatus() != null ? i.getStatus().name() : "")).append(',')
                        .append(csv(category(i.getStatus()))).append(',')
                        .append(csv(i.getFinalRating() != null ? i.getFinalRating().toString() : "")).append(',')
                        .append(csv(i.getPanelRecommendation())).append('\n'));
        return sb.toString();
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    /** Business outcome buckets used by the dashboards and the CSV export. */
    public static String category(InterviewStatus status) {
        if (status == null) {
            return "Others";
        }
        return switch (status) {
            case CANCELLED -> "Cancelled";
            case SUBMITTED, RECOMMENDED, CLOSED -> "Taken";
            default -> "Others"; // SCHEDULED, IN_PROGRESS
        };
    }

    /** Minimal RFC-4180 CSV cell escaping. */
    private String csv(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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
