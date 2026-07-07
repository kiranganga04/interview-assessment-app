package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A bookable availability window for one interviewer. The Schedule Interview wizard books
 * one of these (flipping status to BOOKED and linking it from Interview.slot) rather than
 * letting a recruiter free-type a date/time/interviewer combination.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "interview_slots")
public class InterviewSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "slot_code", nullable = false, length = 40)
    private String slotCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interviewer_id", nullable = false)
    private Interviewer interviewer;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20, nullable = false)
    private InterviewMode mode = InterviewMode.VIRTUAL;

    @Column(name = "technology", length = 150)
    private String technology;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SlotStatus status = SlotStatus.AVAILABLE;
}
