package com.interview.assessment.service;

import com.interview.assessment.dto.InterviewSlotDTO;
import com.interview.assessment.entity.InterviewMode;
import com.interview.assessment.entity.Interviewer;
import com.interview.assessment.entity.InterviewSlot;
import com.interview.assessment.entity.SlotStatus;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.InterviewSlotRepository;
import com.interview.assessment.repository.InterviewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
