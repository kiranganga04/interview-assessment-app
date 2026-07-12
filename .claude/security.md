# Security

## Authentication model: custom opaque tokens, not Spring Security's default flow, not JWT

There is no `UserDetailsService` backing real users and no JWT anywhere in this system. `AuthService` issues a random opaque token (`SecureRandom` + base64 encoding) on successful sign-in or sign-up and stores it in a `user_sessions` row keyed to the user, with an expiry. Every subsequent request must carry that token as `Authorization: Bearer <token>`; `TokenAuthenticationFilter` (`security/TokenAuthenticationFilter.java`) looks the token up in `user_sessions` on **every request** and builds a Spring Security `Authentication` with a single `ROLE_<UserRole>` authority if it's found and unexpired. Because validity is checked server-side per request against a real table (not just decoded from a signed token), revoking a session — via `POST /api/auth/logout`, or an admin deactivating a user — takes effect immediately on the very next request, with no token-expiry window to wait out.

`SecurityConfig` disables `formLogin` and `httpBasic` entirely and replaces Spring Security's default unauthenticated/forbidden handling with `jsonAuthenticationEntryPoint` and `jsonAccessDeniedHandler`, returning plain JSON 401/403 bodies. This matters specifically because Spring Security's default behavior — redirecting an unauthenticated request to an auto-generated `/login` page — breaks CORS for a JS frontend expecting a JSON error, not an HTML redirect.

`/api/auth/**`, `/actuator/health`, and the Swagger/OpenAPI paths are the only routes that `permitAll()`. Every other path under `/api/**` requires a valid, unexpired session token.

## Passwords

Password hashes are stored via Spring Security's `BCryptPasswordEncoder` (`PasswordConfig`) — `app_users.password_hash` is `VARCHAR(100)`, sized for a bcrypt hash, never a reversible encoding. Never log or return a password hash in any API response or log line; none of the existing DTOs (`AuthResponse`, `UserSummaryDTO`, etc.) include it, and new user-facing DTOs should follow that same omission.

## Roles and authorization

Three roles: `ADMIN`, `RECRUITER`, `PANEL` (`entity/UserRole.java`). Authorization is enforced per-endpoint via `@PreAuthorize` on individual controller methods — **there is no centralized route/permission table** on the backend, so the source of truth for "who can call this endpoint" is the specific controller method itself; check there before assuming access based on this document or the frontend's route guards. The frontend duplicates this as a UX convenience (`RoleRoute roles={[...]}` in `App.jsx`) but that duplication must be kept in sync by hand — a frontend guard alone is not a security control, since any authenticated client could call the API directly.

The first account ever created via `/api/auth/signup` becomes `ADMIN` automatically; every account after that defaults to `RECRUITER` or `PANEL` depending on path (confirm which is currently wired in `AuthService.signUp` before relying on either — the root README and `backend/CLAUDE.md` describe this slightly differently, which is worth reconciling if you're touching sign-up). Only an `ADMIN` can promote/demote another user's role or deactivate an account (`UserController`/`UserService`).

`PANEL` is deliberately excluded from `GET /api/interviews` (the full list/browse endpoint) but can still create and view individual interviews — the intent is that panelists submit their own assessments without being able to browse the entire interview pipeline. Don't "simplify" this by giving `PANEL` blanket read access to the list endpoint; it's an intentional restriction, not an oversight.

## Password reset flow

`POST /api/auth/password-reset/request` issues a token into `password_reset_tokens` (with its own expiry and a `used` flag) and attempts to notify the user — logging the reset link by default (`LoggingNotificationService`), or emailing it for real if `app.mail.enabled=true` (`EmailNotificationService`, Gmail SMTP via Spring Mail). Token creation always succeeds even if the notification/email send fails; a failed send is logged as an error but never blocks the flow. `POST /api/auth/password-reset/confirm` consumes the token (marking it used) and updates the password hash. Don't add code that reveals whether a given email address has an account via this endpoint's response — keep the response shape identical whether or not the email matched a real user, consistent with the current implementation's intent.

## CORS

`CorsConfig` builds the allowed-origins list from `app.cors.allowed-origins` (`APP_CORS_ALLOWED_ORIGINS` env var), consumed by `SecurityConfig`'s `.cors(...)`. Local dev intentionally widens this across Vite's auto-incrementing dev ports (5173–5176) so a stray leftover dev server doesn't produce a confusing CORS failure; Docker Compose additionally allows the nginx-served frontend origin. When standing up a new environment, add its real origin here explicitly — don't switch to a wildcard origin, since credentials (the bearer token) are involved.

## Secrets handling

Real credentials (currently: SMTP username/password for outbound email) belong only in `backend/application-local.yml`, which is gitignored and loaded automatically via `spring.config.import: optional:file:./application-local.yml`, or in environment variables (`MAIL_USERNAME`, `MAIL_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`, etc.) — **never** in the tracked `application.yml`, `docker-compose.yml`, or any committed file. `docker-compose.yml` currently sets the MySQL root password (`kiran`) directly in the compose file for local convenience; treat this as a local-only default and override it via environment/secrets management before this ever runs anywhere shared.

A live-looking Gmail App Password was found in `backend/application-local.yml` on disk during this review. That file is correctly gitignored, but since it now contains a real-looking credential, treat it as sensitive: don't paste its contents into chat, tickets, or documentation, and rotate the credential (generate a new Gmail App Password and revoke the old one) if there's any doubt about who has seen it.

## File upload safety

`FileStorageService` allowlists content types (PDF, PNG, JPEG, DOC, DOCX) and enforces a size cap (`app.file-storage.max-size-bytes`, currently 10 MB) on every upload; uploaded files are renamed to a random UUID plus the original extension rather than trusting the client-supplied filename, which avoids path-traversal and filename-collision issues. Any new upload endpoint should reuse `FileStorageService` rather than writing files with a different, un-allowlisted path.

## Error responses

`GlobalExceptionHandler` intentionally returns a generic message for anything unmapped (HTTP 500), rather than the exception's own message or stack trace — don't add a catch-all handler elsewhere that leaks exception internals back to the client. Field-level validation errors are the one case where detail is returned (joined into a single message string on a 400), which is safe since that detail only describes the caller's own malformed input.
