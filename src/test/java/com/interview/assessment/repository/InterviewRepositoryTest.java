package com.interview.assessment.repository;

import com.interview.assessment.entity.Candidate;
import com.interview.assessment.entity.Interview;
import com.interview.assessment.entity.InterviewLevel;
import com.interview.assessment.entity.InterviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module 10: real database round-trip (against H2, MySQL-compatible mode) for the
 * JpaSpecificationExecutor filtering that module 8 (search/pagination) relies on.
 */
@DataJpaTest
class InterviewRepositoryTest {

    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private CandidateRepository candidateRepository;

    @Test
    void filtersByLevelUsingSpecification() {
        Candidate candidate = new Candidate();
        candidate.setCandidateName("Jane Doe");
        candidate = candidateRepository.save(candidate);

        Interview l1 = new Interview();
        l1.setCandidate(candidate);
        l1.setLevelOfInterview(InterviewLevel.L1);
        l1.setStatus(InterviewStatus.SCHEDULED);
        interviewRepository.save(l1);

        Interview l2 = new Interview();
        l2.setCandidate(candidate);
        l2.setLevelOfInterview(InterviewLevel.L2);
        l2.setStatus(InterviewStatus.SCHEDULED);
        interviewRepository.save(l2);

        Specification<Interview> onlyL1 = (root, query, cb) -> cb.equal(root.get("levelOfInterview"), InterviewLevel.L1);

        var page = interviewRepository.findAll(onlyL1, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getLevelOfInterview()).isEqualTo(InterviewLevel.L1);
    }
}
