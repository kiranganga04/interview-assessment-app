# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 3 (Java 17) REST API for the Interview Assessment System — candidate CRUD, a
multi-section interview assessment form (skill ratings, coding rounds, workflow status),
interviewer/slot scheduling, file attachments, an audit trail, and reporting/analytics. This
is the backend half of a full-stack app; the React frontend lives in a sibling `frontend/`
repo (separate git repo, own `CLAUDE.md`) and talks to this service over `/api/**`.

The wider repo layout (one level up from here):
```
interview-assessment-app/
├── database/schema.sql       MySQL schema + seed data (candidates, users, skills, etc.)
├── backend/                  <- this repo
└── frontend/                 React 18 + Vite UI
```

## Commands

```bash
mvn spring-boot:run     # run on :8080 (needs MySQL; see Database below)
mvn test                # unit tests (Mockito) + repository test (H2, no MySQL needed)
mvn test -Dtest=AuthServiceTest              # single test class
mvn test -Dtest=AuthServiceTest#signIn_ok    # single test method
mvn clean package -DskipTests
```

Docker: `Dockerfile` is a two-stage Maven build → `eclipse-temurin:17-jre-alpine` runtime, jar
on `:8080`. `../docker-compose.yml` wires this up with a `mysql` service (auto-seeded from
`../database/schema.sql`) and the frontend container — that's the fastest way to run the
whole stack.

There is no lint/format tool configured (no Checkstyle/Spotless in `pom.xml`) and no CI
workflow file in this repo currently.

## Database

Schema and seed data are owned by `../database/schema.sql`, not JPA — `ddl-auto` is
`validate` (see `application.yml`), so entities must match the existing schema rather than
generate it. Local dev credentials default to `root`/`kiran` against
`interview_assessment_db`, overridable via `SPRING_DATASOURCE_*` env vars.
`backend/application-local.yml` (gitignored) supplies local-only secrets (SMTP creds) via
`spring.config.import: optional:file:./application-local.yml` — never put real credentials
in the tracked `application.yml`.

Tests never touch MySQL: `src/test/resources/application.yml` points repository tests at an
in-memory H2 database in MySQL compatibility mode with `ddl-auto: create-drop`.

## Architecture

**Auth is custom, not Spring Security's default flow.** There's no `UserDetailsService`
backing real users — `AuthService` issues an opaque random token (`SecureRandom` + base64)
into a `user_sessions` table on sign-in/sign-up, and `TokenAuthenticationFilter`
(`security/TokenAuthenticationFilter.java`) validates the `Authorization: Bearer <token>`
header against that table on every request, building a Spring Security `Authentication` with
a single `ROLE_<UserRole>` authority. `SecurityConfig` disables `formLogin`/`httpBasic`
entirely and replaces the default HTML-redirect behavior with plain JSON 401/403 bodies
(`jsonAuthenticationEntryPoint` / `jsonAccessDeniedHandler`) — this matters because Spring
Security's default unauthenticated-request handling (redirect to an auto-generated `/login`)
breaks CORS for a JS frontend. `/api/auth/**`, `/actuator/health`, and swagger paths are the
only routes that `permitAll()`; everything else under `/api/**` requires a valid session.

**Roles are `ADMIN` / `RECRUITER` / `PANEL`** (`entity/UserRole.java`), enforced per-endpoint
via `@PreAuthorize` on controller methods (method security enabled in `SecurityConfig` via
`@EnableMethodSecurity`) — there's no centralized route table, so check the specific
controller when changing access. The first user to ever sign up becomes `ADMIN`
automatically (`AuthService.signUp`); everyone after that defaults to `PANEL`. Only an
`ADMIN` can promote/deactivate other users (`UserController`, `UserService`). Note `PANEL` is
deliberately excluded from `GET /api/interviews` (list/browse) but can still create and view
individual interviews — panel members submit assessments, they don't browse the full list.

**Interview workflow is a server-enforced state machine.** `InterviewStatus` transitions
(`SCHEDULED → IN_PROGRESS → SUBMITTED → RECOMMENDED → CLOSED`, plus `CANCELLED`) are validated
against an explicit `ALLOWED_TRANSITIONS` map in `InterviewService` — status can only change
through `PATCH /api/interviews/{id}/status` (→ `changeStatus()`), never via a plain `PUT`
(`applyFields()` only honors an incoming `status` on *create*, specifically to prevent a PUT
from bypassing transition rules). Cancelling an interview that came from a booked slot
releases the slot back to `AVAILABLE` (`releaseSlotIfAny`).

**Two ways to create an interview.** Plain `POST /api/interviews` (`InterviewService.create`)
free-types panel/date/time and is used by the "New assessment" form, available to all three
roles. `POST /api/interviews/schedule` (`scheduleFromSlot`) books an `AVAILABLE`
`InterviewSlot` (flips it to `BOOKED`), pulling interviewer/date/time/mode from the slot
instead — `ADMIN`/`RECRUITER` only, same scope as `InterviewerController`/
`InterviewSlotController`. Both are independent, additive paths; don't assume one replaces
the other.

**`InterviewDTO` is a single nested payload** for the whole assessment form: it carries
`internalSkillAssessments`, `clientSkillAssessments` (both backed by one `SkillAssessment`
entity/table, discriminated by `PanelType.INTERNAL`/`CLIENT` — this is "Module notes" #2 in
the root README: one table serves both the internal panel and the client technical panel
sections rather than duplicating schema), and `codingRounds`. `InterviewService.applyFields`
does full replace-on-write for both collections (clear + rebuild from the DTO) rather than a
diff/merge — keep that in mind when extending it, since IDs on existing rows aren't
preserved across an update.

**Auditing is two independent mechanisms, don't conflate them:**
- `AuditableEntity` (mapped superclass) + `JpaAuditingConfig`/`CurrentUserAuditorAware`
  auto-stamp `created_at`/`updated_at`/`created_by`/`updated_by` on every entity via Spring
  Data JPA auditing — passive, no code needed per-entity beyond extending it.
- `AuditService.record(...)` is an explicit, service-level audit log written to `audit_logs`
  for domain actions (CREATE/UPDATE/DELETE/STATUS_CHANGE/SCHEDULE) — called manually at the
  point of each mutation in `InterviewService` and other services. Adding a new mutating
  action means adding this call yourself; it isn't automatic.

Both derive "who did this" from `CurrentUser.emailOrSystem()` (`security/CurrentUser.java`),
which reads `SecurityContextHolder` and falls back to `"system"` when unauthenticated.

**Notifications are a swappable interface, not wired to real email by default.**
`NotificationService` is the interface; `LoggingNotificationService` (just logs) is what's
active unless `app.mail.enabled=true`, in which case `EmailNotificationService` (Spring
Mail/JavaMailSender, Gmail SMTP) takes over — see which bean is `@Primary`/conditional if
you're touching this. Failed email sends are logged as errors but never block the underlying
operation (e.g. password-reset token creation still succeeds even if the email fails).

**File storage (`FileStorageService`)** is deliberately narrow/interface-shaped so it can be
swapped for S3 later — local disk only today, path from `app.file-storage.directory`
(`FileStorageProperties`), files renamed to a random UUID + original extension on write,
content-type allowlisted (PDF/PNG/JPEG/DOC/DOCX) and size-capped
(`app.file-storage.max-size-bytes`). `Attachment` rows are polymorphic via
`AttachmentOwnerType` + `ownerId` rather than per-entity foreign keys, so one table backs
attachments on any owner type (candidate resumes, interview screenshots, etc.).

**Reporting (`ReportService`, `ReportController`) aggregates in memory** over already-loaded
entities rather than SQL-side aggregation — acceptable at this data scale per the root
README's noted limitation; revisit if interview volume grows substantially.

**Global exception handling** (`exception/GlobalExceptionHandler`) maps
`ResourceNotFoundException`→404, `BadRequestException`/`IllegalArgumentException`→400,
`BadCredentialsException`→401, `AccessDeniedException`→403, validation errors→400 (with
field-level messages joined into one string), and everything else→500 with a generic message
— don't leak exception internals through a new handler; follow this pattern.

**CORS** is configured once in `CorsConfig` from `app.cors.allowed-origins` (comma-separated,
env-overridable) and consumed by `SecurityConfig`'s `.cors(...)` — origins are widened in
local dev to cover Vite's auto-incrementing dev-server ports (5173–5176) so a stray leftover
`npm run dev` process doesn't produce a confusing CORS failure.