package com.interview.assessment.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Small helper so services/controllers can ask "who is calling right now?" without wiring SecurityContextHolder everywhere. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String emailOrSystem() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return "system";
        }
        return authentication.getName();
    }
}
