package com.interview.assessment.security;

import com.interview.assessment.entity.AppUser;
import com.interview.assessment.entity.UserSession;
import com.interview.assessment.repository.AppUserRepository;
import com.interview.assessment.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Module 1 (security): validates the "Authorization: Bearer <token>" header against the
 * user_sessions table on every request. This is the piece that was previously missing —
 * the frontend already sent the header, but nothing on the backend checked it, so every
 * API endpoint was reachable without logging in at all.
 */
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final UserSessionRepository userSessionRepository;
    private final AppUserRepository appUserRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            authenticate(token).ifPresent(user -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
                auth.setDetails(user.getUserId());
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        filterChain.doFilter(request, response);
    }

    private Optional<AppUser> authenticate(String token) {
        Optional<UserSession> session = userSessionRepository.findByAuthToken(token);
        if (session.isEmpty() || session.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        return appUserRepository.findById(session.get().getUserId()).filter(AppUser::isActive);
    }
}
