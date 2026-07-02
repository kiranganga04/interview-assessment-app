package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "candidate_name", nullable = false, length = 150)
    private String candidateName;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "overall_experience", length = 20)
    private String overallExperience;

    @Column(name = "current_role", length = 150)
    private String currentRole;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Interview> interviews = new ArrayList<>();
}
