package com.interview.assessment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * People Management: a bookable directory of people who conduct interviews.
 * Deliberately separate from AppUser/PANEL logins -- see schema.sql's comment on the
 * interviewers table for why. Slots (InterviewSlot) hang off an interviewer; the
 * Schedule Interview wizard books a slot, which is how an interview ends up assigned
 * to one of these.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "interviewers")
public class Interviewer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interviewer_id")
    private Long interviewerId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(name = "account", length = 150)
    private String account;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "level_capability", length = 50)
    private String levelCapability;

    @Column(name = "skill_set", length = 255)
    private String skillSet;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
