package com.interview.assessment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coding_rounds")
public class CodingRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coding_round_id")
    private Long codingRoundId;

    @JsonIgnore
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
    @Column(name = "test_complexity", length = 20)
    private TestComplexity testComplexity;

    @Enumerated(EnumType.STRING)
    @Column(name = "coding_status", length = 20)
    private CodingStatus codingStatus;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
