package com.interview.assessment.repository;

import com.interview.assessment.entity.InterviewSlot;
import com.interview.assessment.entity.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, Long> {

    List<InterviewSlot> findByStatusAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(
            SlotStatus status, LocalDate fromDate);

    List<InterviewSlot> findAllByOrderBySlotDateDescStartTimeAsc();

    long countBySlotDate(LocalDate slotDate);
}
