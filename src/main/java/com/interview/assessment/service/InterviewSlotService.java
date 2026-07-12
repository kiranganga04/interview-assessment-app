package com.interview.assessment.service;

import com.interview.assessment.dto.BulkImportResultDTO;
import com.interview.assessment.dto.InterviewSlotDTO;
import com.interview.assessment.dto.PageResponse;
import com.interview.assessment.entity.InterviewMode;
import com.interview.assessment.entity.Interviewer;
import com.interview.assessment.entity.InterviewSlot;
import com.interview.assessment.entity.SlotStatus;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.InterviewSlotRepository;
import com.interview.assessment.repository.InterviewerRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD + browsing for interviewer availability windows (Interview Management module).
 * Booking/releasing a slot against an actual interview is handled by InterviewService
 * (it owns the Interview<->InterviewSlot relationship and needs both repositories in the
 * same transaction), not here -- this service only manages the slot's own lifecycle.
 */
@Service
@RequiredArgsConstructor
public class InterviewSlotService {

    private static final DateTimeFormatter CODE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Sanity limit on a single Bulk Import upload -- real usage is a handful of dozens of
     * rows at a time; this just guards against an accidental multi-thousand-row paste. */
    private static final int MAX_BULK_IMPORT_ROWS = 500;

    private final InterviewSlotRepository interviewSlotRepository;
    private final InterviewerRepository interviewerRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<InterviewSlotDTO> listAll() {
        return interviewSlotRepository.findAllByOrderBySlotDateDescStartTimeAsc().stream()
                .map(this::toDto)
                .toList();
    }

    /** Only AVAILABLE slots from today onward -- what the Schedule Interview wizard offers. */
    @Transactional(readOnly = true)
    public List<InterviewSlotDTO> listAvailableFrom(LocalDate fromDate) {
        return interviewSlotRepository
                .findByStatusAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(SlotStatus.AVAILABLE, fromDate)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Interview Management: page/size/sort + search/status/mode filters for the Interview
     * Slots directory table, mirroring InterviewService.search()'s Specification + Pageable
     * pattern (module 8). Deliberately separate from listAll()/listAvailableFrom() above --
     * those back the Schedule Interview wizard's slot picker, which needs the un-paginated
     * AVAILABLE-from-today list, not one page of the full history.
     */
    @Transactional(readOnly = true)
    public PageResponse<InterviewSlotDTO> searchPaged(String search, String status, String mode, Pageable pageable) {
        Specification<InterviewSlot> spec = buildSpecification(search, status, mode);
        Page<InterviewSlot> page = interviewSlotRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    private Specification<InterviewSlot> buildSpecification(String search, String status, String mode) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(search)) {
                // Only join the interviewer table when a search term actually needs it, rather
                // than paying for the join on every request (status/mode-only filters, or no
                // filter at all -- the common case when just paging through the list).
                Join<InterviewSlot, Interviewer> interviewerJoin = root.join("interviewer");
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("slotCode")), like),
                        cb.like(cb.lower(interviewerJoin.get("fullName")), like),
                        cb.like(cb.lower(interviewerJoin.get("email")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("technology"), "")), like)));
            }
            if (StringUtils.hasText(status)) {
                try {
                    predicates.add(cb.equal(root.get("status"), SlotStatus.valueOf(status.toUpperCase())));
                } catch (IllegalArgumentException ignored) {
                    // unrecognized status value -- treat as "no filter" rather than erroring out
                }
            }
            if (StringUtils.hasText(mode)) {
                try {
                    predicates.add(cb.equal(root.get("mode"), InterviewMode.valueOf(mode.toUpperCase())));
                } catch (IllegalArgumentException ignored) {
                    // unrecognized mode value -- treat as "no filter" rather than erroring out
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public InterviewSlotDTO get(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public InterviewSlotDTO create(InterviewSlotDTO dto) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BadRequestException("End time must be after start time.");
        }
        InterviewSlot slot = new InterviewSlot();
        slot.setInterviewer(findInterviewer(dto.getInterviewerId()));
        slot.setSlotDate(dto.getSlotDate());
        slot.setStartTime(dto.getStartTime());
        slot.setEndTime(dto.getEndTime());
        slot.setMode(parseMode(dto.getMode()));
        slot.setTechnology(dto.getTechnology());
        slot.setStatus(SlotStatus.AVAILABLE);
        slot.setSlotCode(generateSlotCode(dto.getSlotDate()));
        slot = interviewSlotRepository.save(slot);
        auditService.record("InterviewSlot", slot.getSlotId(), "CREATE", slot.getSlotCode());
        return toDto(slot);
    }

    /**
     * Interview Management (Bulk Import): parses a CSV upload row-by-row and creates a slot
     * for each valid row -- "import the good rows, report the bad ones" rather than an
     * all-or-nothing batch, so one typo doesn't block the other 39 rows. No row-level
     * exception ever escapes this method (each row is individually try/caught), so the
     * surrounding @Transactional only rolls back on something truly unexpected, not on
     * ordinary bad input like an unknown email or a malformed date.
     *
     * Deliberately does NOT check for overlapping/duplicate slots for the same interviewer --
     * neither does the single "+ Add Slot" form (create() above), and this stays consistent
     * with that rather than silently enforcing a stricter rule in only one of the two paths.
     */
    @Transactional
    public BulkImportResultDTO bulkImportFromCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            throw new BadRequestException("The uploaded file is empty.");
        }
        BulkImportResultDTO result = new BulkImportResultDTO();
        String[] lines = csv.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        boolean headerSkipped = false;
        int dataRowCount = 0;

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i];
            int rowNumber = i + 1; // 1-based, matching what a spreadsheet/text editor shows
            if (!StringUtils.hasText(rawLine)) {
                continue; // silently skip blank lines, e.g. a trailing newline at EOF
            }
            if (!headerSkipped) {
                headerSkipped = true;
                continue; // the first non-blank line is always treated as the header row
            }
            dataRowCount++;
            if (dataRowCount > MAX_BULK_IMPORT_ROWS) {
                result.getErrors().add(new BulkImportResultDTO.RowError(rowNumber,
                        "Skipped -- bulk import is limited to " + MAX_BULK_IMPORT_ROWS + " rows per file."));
                continue;
            }
            try {
                InterviewSlot slot = parseAndBuildSlot(parseCsvLine(rawLine));
                slot.setSlotCode(generateSlotCode(slot.getSlotDate()));
                slot = interviewSlotRepository.save(slot);
                result.getCreatedSlotCodes().add(slot.getSlotCode());
                auditService.record("InterviewSlot", slot.getSlotId(), "BULK_IMPORT", slot.getSlotCode());
            } catch (BadRequestException | ResourceNotFoundException ex) {
                result.getErrors().add(new BulkImportResultDTO.RowError(rowNumber, ex.getMessage()));
            } catch (Exception ex) {
                result.getErrors().add(new BulkImportResultDTO.RowError(rowNumber, "Could not process this row: " + ex.getMessage()));
            }
        }

        result.setTotalRows(dataRowCount);
        result.setCreatedCount(result.getCreatedSlotCodes().size());
        result.setErrorCount(result.getErrors().size());
        return result;
    }

    @Transactional
    public InterviewSlotDTO update(Long id, InterviewSlotDTO dto) {
        InterviewSlot slot = findOrThrow(id);
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new BadRequestException("Cannot edit a slot that's already booked; cancel the interview first.");
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BadRequestException("End time must be after start time.");
        }
        if (!slot.getInterviewer().getInterviewerId().equals(dto.getInterviewerId())) {
            slot.setInterviewer(findInterviewer(dto.getInterviewerId()));
        }
        slot.setSlotDate(dto.getSlotDate());
        slot.setStartTime(dto.getStartTime());
        slot.setEndTime(dto.getEndTime());
        slot.setMode(parseMode(dto.getMode()));
        slot.setTechnology(dto.getTechnology());
        slot = interviewSlotRepository.save(slot);
        auditService.record("InterviewSlot", slot.getSlotId(), "UPDATE", slot.getSlotCode());
        return toDto(slot);
    }

    @Transactional
    public void cancel(Long id) {
        InterviewSlot slot = findOrThrow(id);
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new BadRequestException("Cannot cancel a slot that's already booked; cancel the interview first.");
        }
        slot.setStatus(SlotStatus.CANCELLED);
        interviewSlotRepository.save(slot);
        auditService.record("InterviewSlot", slot.getSlotId(), "CANCEL", slot.getSlotCode());
    }

    private InterviewSlot findOrThrow(Long id) {
        return interviewSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview slot not found: " + id));
    }

    private Interviewer findInterviewer(Long interviewerId) {
        return interviewerRepository.findById(interviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found: " + interviewerId));
    }

    private InterviewMode parseMode(String raw) {
        try {
            return InterviewMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown mode: " + raw);
        }
    }

    /** Bulk Import: build one InterviewSlot from a parsed CSV row's cells. Columns match the
     * downloadable template: Interviewer Email, Date, Start Time, End Time, Mode, Technology. */
    private InterviewSlot parseAndBuildSlot(List<String> cells) {
        if (cells.size() < 5) {
            throw new BadRequestException("Expected at least 5 columns (Interviewer Email, Date, "
                    + "Start Time, End Time, Mode) -- found " + cells.size() + ".");
        }
        String email = cells.get(0).trim();
        String dateRaw = cells.get(1).trim();
        String startRaw = cells.get(2).trim();
        String endRaw = cells.get(3).trim();
        String modeRaw = cells.get(4).trim();
        String technology = cells.size() > 5 ? cells.get(5).trim() : null;

        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("Interviewer email is required.");
        }
        Interviewer interviewer = interviewerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("No interviewer found with email: " + email));

        LocalDate slotDate = parseCsvDate(dateRaw);
        LocalTime startTime = parseCsvTime(startRaw);
        LocalTime endTime = parseCsvTime(endRaw);
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("End time must be after start time.");
        }
        InterviewMode mode = parseModeFriendly(modeRaw);

        InterviewSlot slot = new InterviewSlot();
        slot.setInterviewer(interviewer);
        slot.setSlotDate(slotDate);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setMode(mode);
        slot.setTechnology(StringUtils.hasText(technology) ? technology : null);
        slot.setStatus(SlotStatus.AVAILABLE);
        return slot;
    }

    /** "uuuu" (absolute year), not "yyyy" (year-of-era), is required for ResolverStyle.STRICT to
     * resolve a date without an explicit Era field -- with "yyyy" under STRICT it throws even
     * for perfectly valid input. STRICT (rather than the default SMART) matters too: SMART
     * silently clamps an invalid day-of-month like Feb 31 down to Feb 28 instead of rejecting
     * it, which would be exactly the kind of silent-wrong-data bug this validation exists to
     * avoid. Verified against both behaviors with a standalone test harness before shipping. */
    private static final DateTimeFormatter CSV_DAY_FIRST_DASH =
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter CSV_DAY_FIRST_SLASH =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Accepts YYYY-MM-DD (tried first) or DD-MM-YYYY / DD/MM/YYYY (day-first, matching the
     * regional format already used elsewhere in this app's data) as a fallback. For a value
     * where both parts could plausibly be a day or a month (e.g. "03-04-2026"), day-first wins
     * once ISO parsing has failed -- there's no way to guess intent from the string alone, and
     * day-first matches the rest of the app's regional conventions. */
    private LocalDate parseCsvDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException("Date is required (format: YYYY-MM-DD or DD-MM-YYYY).");
        }
        String trimmed = raw.trim();
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException isoEx) {
            try {
                return LocalDate.parse(trimmed, CSV_DAY_FIRST_DASH);
            } catch (DateTimeParseException dashEx) {
                try {
                    return LocalDate.parse(trimmed, CSV_DAY_FIRST_SLASH);
                } catch (DateTimeParseException slashEx) {
                    throw new BadRequestException("Could not read date \"" + raw
                            + "\" -- use YYYY-MM-DD (e.g. 2026-07-10) or DD-MM-YYYY (e.g. 10-07-2026).");
                }
            }
        }
    }

    private LocalTime parseCsvTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException("Time is required (24-hour format HH:MM).");
        }
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Could not read time \"" + raw + "\" -- use 24-hour HH:MM (e.g. 14:30).");
        }
    }

    /** Accepts either the raw enum values (VIRTUAL/IN_PERSON/TELEPHONIC) or the friendly labels
     * shown in the UI (Online/In-Person/Telephonic) so a non-technical CSV author isn't expected
     * to know the internal enum names. */
    private InterviewMode parseModeFriendly(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException("Mode is required (Online, In-Person, or Telephonic).");
        }
        String normalized = raw.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if ("ONLINE".equals(normalized)) {
            return InterviewMode.VIRTUAL;
        }
        try {
            return InterviewMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown mode \"" + raw + "\" -- use Online, In-Person, or Telephonic.");
        }
    }

    /** Minimal RFC4180-style CSV line parser: handles quoted fields containing commas, and
     * doubled "" as an escaped quote inside a quoted field. Good enough for a Technology
     * column value like "Java, React" without pulling in a CSV library dependency. */
    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private String generateSlotCode(LocalDate date) {
        long countSoFar = interviewSlotRepository.countBySlotDate(date);
        return "SLOT-" + date.format(CODE_DATE_FORMAT) + "-" + String.format("%04d", countSoFar + 1);
    }

    private InterviewSlotDTO toDto(InterviewSlot slot) {
        InterviewSlotDTO dto = new InterviewSlotDTO();
        dto.setSlotId(slot.getSlotId());
        dto.setSlotCode(slot.getSlotCode());
        dto.setInterviewerId(slot.getInterviewer().getInterviewerId());
        dto.setInterviewerName(slot.getInterviewer().getFullName());
        dto.setInterviewerEmail(slot.getInterviewer().getEmail());
        dto.setInterviewerContact(slot.getInterviewer().getContactNumber());
        dto.setAccount(slot.getInterviewer().getAccount());
        dto.setGrade(slot.getInterviewer().getGrade());
        dto.setLevelCapability(slot.getInterviewer().getLevelCapability());
        dto.setSlotDate(slot.getSlotDate());
        dto.setStartTime(slot.getStartTime());
        dto.setEndTime(slot.getEndTime());
        dto.setMode(slot.getMode().name());
        dto.setTechnology(slot.getTechnology());
        dto.setStatus(slot.getStatus().name());
        return dto;
    }
}
