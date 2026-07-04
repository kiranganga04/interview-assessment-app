package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candidates")
public class Candidate extends AuditableEntity {

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
}
