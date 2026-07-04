package com.interview.assessment.repository;

import com.interview.assessment.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByAuthToken(String authToken);
}
