# Project

## What this is

The Interview Assessment System is a full-stack internal tool for running and tracking technical interviews end-to-end: scheduling an interviewer against a bookable slot, capturing a structured skill-by-skill assessment (self-rating, panel rating, feedback) for both an internal panel round and a separate client technical panel round, recording a coding-round result, moving the interview through a workflow status, and reporting on pass rates, skill averages, and panelist calibration across everyone who has interviewed.

It grew out of a single paper "Interview Assessment Form" (candidate details, panel skill ratings, a coding-round table, a client technical panel section) and has since been extended with authentication and role-based access, a bookable interviewer/slot scheduling module, a configurable skill catalog, file attachments (resumes, screenshots), an audit trail, and a reporting dashboard. The product name shown in the UI is "Interview Assessment System" with the tagline "Panel evaluation workspace" (`frontend/src/config/navigation.js`).

## Who uses it

Three roles, enforced identically on both the backend (`@PreAuthorize`) and the frontend (route guards):

`ADMIN` — full access, including user administration (`/users`: promote/demote roles, activate/deactivate accounts). The first person to ever sign up becomes `ADMIN` automatically; everyone after that defaults to `RECRUITER` (Docker Compose path) or `PANEL` (plain `mvn`/`npm` path per `AuthService.signUp` — confirm which default is live before assuming either).

`RECRUITER` — manages candidates, schedules interviews, browses the full interview list, manages interviewers and slots, views analytics.

`PANEL` — the interviewer conducting a round. Can create and view individual interview assessments (their own work) but is deliberately excluded from browsing the full `/interviews` list — panelists submit assessments, they don't browse the pipeline.

## Core domain concepts

A **Candidate** is a person being interviewed: name, mobile number, overall experience, current role. A candidate can have many **Interviews** — one row per interview round (L1/L2/L3/HR/CLIENT level), each carrying its own status, ratings, and feedback.

An **Interview** is the central record. It has a level (`L1`, `L2`, `L3`, `HR`, `CLIENT`), a mode (`VIRTUAL`, `IN_PERSON`, `TELEPHONIC`), a workflow status (`SCHEDULED → IN_PROGRESS → SUBMITTED → RECOMMENDED → CLOSED`, plus `CANCELLED`), and nests two collections of **Skill Assessments** — `internalSkillAssessments` for the internal panel section and `clientSkillAssessments` for the "to be filled by client technical panel" section — plus zero or more **Coding Rounds** (problems attempted, time taken, complexity, completion status). One `skill_assessments` table backs both collections, discriminated by a `panel_type` column (`INTERNAL`/`CLIENT`), rather than duplicating the schema.

An interview can be created two ways: a plain free-typed form (`POST /api/interviews`, open to all three roles) where the recruiter or panelist types the panel member, date, and time directly, or booked against a pre-published **Interview Slot** (`POST /api/interviews/schedule`, `ADMIN`/`RECRUITER` only) — this pulls interviewer, date, time, and mode from the slot and flips the slot from `AVAILABLE` to `BOOKED`. These are independent, additive paths, not two versions of the same flow.

An **Interviewer** is a bookable resource — a person with capacity/skill metadata (client account they support, grade, level capability, skill set) who can publish **Interview Slots** (availability windows with a mode and a technology tag). This is intentionally separate from an `app_users`/`PANEL` login: an interviewer is schedulable via slots whether or not they ever sign into the system, and a `PANEL` login doesn't necessarily have a slot-bookable interviewer row. The two can overlap by email in practice.

The **Skill Catalog** (`skills` table) is an admin-managed list of skill names with which interview levels they apply to (e.g. "Core Java" for L1/L2/L3), backing an autocomplete on the assessment form instead of free-typed skill names only.

**Attachments** (resumes on a candidate, screenshots/exports on an interview) are stored on local disk today (path from `app.file-storage.directory`, swappable to S3 later) and modeled polymorphically — one `attachments` table with an `owner_type` (`CANDIDATE_RESUME` / `INTERVIEW_SCREENSHOT`) and `owner_id` rather than a foreign key per owner type.

Every mutation is captured twice, for different purposes: passive created/updated-by/at stamps on every row (Spring Data JPA auditing), and an explicit, human-readable `audit_logs` entry per domain action (create/update/delete/status-change/schedule) written by `AuditService`.

## Tech stack at a glance

Backend: Spring Boot 3 / Java 17, Spring Data JPA + MySQL 8 (schema owned by hand-written SQL, not Hibernate DDL), Spring Security with a custom opaque-token auth scheme (not the default form-login flow), springdoc-openapi (Swagger UI), Spring Mail for password-reset email, Lombok, JUnit5/Mockito + H2 for tests, Maven.

Frontend: React 18 + Vite, react-router-dom v6, axios, no state-management library (auth state is a single `useState` in `App`, hydrated from `localStorage`), a single global stylesheet (no CSS-in-JS, no Tailwind).

Data: MySQL 8, schema and seed data owned by `database/schema.sql` plus an additive `database/migration_v2_interview_slots.sql` for databases created before the interviewer/slot module existed.

Deployment: Docker Compose wires up MySQL (auto-seeded from `schema.sql` on first volume init only), the Spring Boot backend, and an nginx-served frontend build that proxies `/api/*` to the backend.

## Module map

`backend/src/main/java/com/interview/assessment/` — `config` (CORS, JPA auditing, OpenAPI, password encoder, Spring Security), `controller` (one per resource: Auth, Candidate, File, Interview, Interviewer, InterviewSlot, Report, SkillCatalog, User), `dto` (request/response payloads, kept separate from entities), `entity` (JPA entities + enums), `exception` (custom exceptions + a single `GlobalExceptionHandler`), `repository` (Spring Data JPA repositories), `security` (token filter, current-user resolution), `service` (business logic — one service per resource plus cross-cutting `AuditService`, `FileStorageService`, `NotificationService`).

`frontend/src/` — `pages` (one file per screen, self-contained state), `components` (shared display widgets: `RatingBadge`, `SkillAssessmentTable`, `CodingRoundTable`) and `components/layout` (app shell: `Sidebar`, `TopBar`, `SiteFooter`, `RouteGuards`, `ToastProvider`), `api/apiClient.js` (single axios instance, every backend call as a named export), `config/navigation.js` (branding/nav constants).

`database/` — `schema.sql` (full schema + demo seed data, MySQL-only, drops and recreates the database on every run) and `migration_v2_interview_slots.sql` (additive migration for the interviewer/slot module against an already-running database).

## Where to look next

See `architecture.md` for how the pieces fit together and request flow, `security.md` for the auth model and role matrix, `database-rules.md` for schema conventions, `api-guidelines.md` for REST conventions, `coding-standards.md` and `ui-guidelines.md` for how to write new code consistently, and `testing.md` for what's covered today and how to test new work.
