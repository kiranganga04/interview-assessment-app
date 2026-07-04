package com.interview.assessment.repository;

import com.interview.assessment.entity.CodingRound;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodingRoundRepository extends JpaRepository<CodingRound, Long> {
}
