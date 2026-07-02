package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coding_rounds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coding_round_id")
    private Long codingRoundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(name = "skill", length = 100)
    private String skill;

    @Column(name = "no_of_questions")
    private Integer noOfQuestions;

    @Column(name = "time_taken_mins")
    private Integer timeTakenMins;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_complexity")
    private TestComplexity testComplexity;

    @Enumerated(EnumType.STRING)
    @Column(name = "coding_status")
    private CodingStatus codingStatus;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
