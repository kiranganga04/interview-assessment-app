package com.interview.assessment.repository;

import com.interview.assessment.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    List<Skill> findByActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
