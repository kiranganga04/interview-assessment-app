package com.interview.assessment.config;

import com.interview.assessment.security.TokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Module 1 (security). Everything under /api/** requires a valid session token
 * (see TokenAuthenticationFilter) except the auth endpoints themselves. Role checks
 * for individual operations (e.g. only ADMIN can manage users/skill catalog) live on
 * the controller methods via @PreAuthorize, enabled here with @EnableMethodSecurity.
 *
 * This is a stateless, token-based REST API, not a browser form-login site, so formLogin
 * and httpBasic are explicitly disabled below. Leaving them at Spring Security's defaults
 * (i.e. never mentioning them at all) silently keeps its built-in form-login machinery
 * active: an auto-generated "/login" page, a random in-memory user whose password is
 * printed to the console on every startup ("Using generated security password: ..."), and
 * — critically — unauthenticated requests get redirected to that HTML "/login" page instead
 * of receiving a clean 401. A browser XHR/fetch call that gets redirected to a different
 * origin's page then fails CORS, which is exactly the "blocked by CORS policy ... redirected
 * from .../signup to .../login" error this replaces. None of that default machinery is
 * appropriate here or something a real client would expect, so it's turned off entirely and
 * replaced with plain JSON error responses below.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()))
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * We authenticate purely via TokenAuthenticationFilter against our own user_sessions
     * table (see that class) rather than Spring Security's standard
     * UserDetailsService/AuthenticationManager flow. Providing this no-op bean satisfies
     * UserDetailsServiceAutoConfiguration's "back off if a UserDetailsService already
     * exists" condition, so it stops generating an in-memory default user with a random
     * password on every startup — that mechanism is never consulted by this app and a
     * random per-restart password isn't an appropriate stand-in for real authentication.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(
                    "This application authenticates via bearer tokens (user_sessions table), "
                            + "not Spring Security's built-in user store.");
        };
    }

    /** Unauthenticated request to a protected endpoint -> plain 401 JSON, never an HTML redirect. */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> writeJsonError(response,
                HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                "Authentication is required to access this resource.", request.getRequestURI());
    }

    /** Authenticated but insufficient role/permission -> plain 403 JSON. */
    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeJsonError(response,
                HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                "You do not have permission to perform this action.", request.getRequestURI());
    }

    private void writeJsonError(HttpServletResponse response, int status, String error,
                                 String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                LocalDateTime.now(), status, error, message, path));
    }
}
