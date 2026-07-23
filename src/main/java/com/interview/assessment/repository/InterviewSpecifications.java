package com.interview.assessment.repository;

import com.interview.assessment.entity.Interview;
import com.interview.assessment.security.CurrentUser;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * RBAC row-visibility for assessment records, applied uniformly to the Assessments list, the
 * single-record fetch, and every Dashboard Overview / Analytics aggregation so all three always
 * agree (no "the list shows a record the dashboard hides" leak):
 *
 *   ADMIN      -> every interview (no restriction).
 *   RECRUITER  -> only interviews they own: created_by == their login email (the recruiter who
 *                 scheduled/created it, stamped automatically by JPA auditing) OR recruiter_email
 *                 == their login email (the assigned recruiter). Matched case-insensitively.
 *   PANEL      -> only interviews assigned to them as the interviewer (interviewer.email).
 *   anyone else / unauthenticated -> nothing.
 *
 * The email key is reliable because the auth principal name IS the user's email
 * (see TokenAuthenticationFilter) -- the same value written into created_by. Rows whose
 * created_by / recruiter_email are null simply never match a recruiter, which is the intended
 * "don't attribute legacy/unassigned rows to anyone" behaviour.
 */
public final class InterviewSpecifications {

    private InterviewSpecifications() {
    }

    public static Specification<Interview> visibleToCurrentUser() {
        final String email = CurrentUser.emailOrSystem();
        final String lowerEmail = email == null ? null : email.toLowerCase();
        final boolean admin = CurrentUser.hasRole("ADMIN");
        final boolean recruiter = CurrentUser.hasRole("RECRUITER");
        final boolean panel = CurrentUser.hasRole("PANEL");

        return (root, query, cb) -> {
            if (admin) {
                return cb.conjunction(); // unrestricted
            }
            if (lowerEmail == null) {
                return cb.disjunction(); // no authenticated identity -> no rows
            }
            if (recruiter) {
                Predicate createdByMe = cb.equal(cb.lower(root.get("createdBy")), lowerEmail);
                Predicate assignedToMe = cb.equal(cb.lower(root.get("recruiterEmail")), lowerEmail);
                return cb.or(createdByMe, assignedToMe);
            }
            if (panel) {
                return cb.equal(cb.lower(root.join("interviewer", JoinType.LEFT).get("email")), lowerEmail);
            }
            return cb.disjunction(); // unknown role -> no rows
        };
    }
}
