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

    // Backtick-quoted on purpose: "current_role" collides with H2's reserved CURRENT_ROLE
    // keyword (an ANSI SQL session function), which breaks DDL generation for the H2 test
    // database ("expected identifier"). Hibernate treats a backtick-wrapped @Column name as
    // "quote this identifier" and renders it with whatever the active dialect's quote
    // character is -- double quotes on H2, backticks on MySQL -- so this fixes the H2 test
    // run without changing the physical column name or affecting production MySQL, which
    // doesn't reserve this word at all.
    @Column(name = "`current_role`", length = 150)
    private String currentRole;
}
