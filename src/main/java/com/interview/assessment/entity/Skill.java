package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Module 4: admin-managed skill catalog so panelists pick from a curated list
 * instead of retyping skill names on every assessment.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    /** Comma-separated interview levels this skill applies to, e.g. "L1,L2,L3". Blank = all levels. */
    @Column(name = "applicable_levels", length = 100)
    private String applicableLevels;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
