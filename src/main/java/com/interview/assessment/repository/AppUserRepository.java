package com.interview.assessment.repository;

import com.interview.assessment.entity.AppUser;
import com.interview.assessment.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    // Used by UserService to block demoting/deactivating the last remaining active Admin.
    long countByRoleAndActiveTrue(UserRole role);
}
