# Architecture

## Shape of the system

Two independently deployable, independently versioned pieces (separate git repositories, each with its own `CLAUDE.md`) plus a hand-owned SQL schema that neither one generates:

```
interview-assessment-app/
├── database/schema.sql          MySQL schema + seed data (owned by hand, not JPA)
├── database/migration_v2_...    additive migration for an already-running database
├── backend/                     Spring Boot 3 (Java 17) REST API, :8080
├── frontend/                    React 18 + Vite UI, :5173 dev / :80 in the nginx image
└── docker-compose.yml           mysql + backend + frontend, the fastest way to run all three
```

The frontend never talks to MySQL directly and the backend never serves HTML — the only contract between the two is the JSON REST API under `/api/**`, documented in `api-guidelines.md` and importable as `postman_collection.json`.

## Backend: layered, request → controller → service → repository → entity

Each resource follows the same shape: a `@RestController` in `controller/` accepts a request, delegates to a `service/` class that holds the business logic and transaction boundary, which reads/writes through a Spring Data JPA `repository/` interface against an `entity/` class, and returns a `dto/` object shaped for the frontend rather than the entity itself. DTOs and entities are kept deliberately separate — the DTO for the interview form (`InterviewDTO`) nests `internalSkillAssessments`, `clientSkillAssessments`, and `codingRounds` so the whole assessment form is created or updated in a single call, while the underlying entities are three separate JPA-mapped tables.

`ddl-auto` is `validate`, not `update` or `create` — the schema in `database/schema.sql` is the source of truth, and entities are written to match it, not the other way around. Changing a column means editing `schema.sql` (and, for an already-running database, an additive migration script like `migration_v2_interview_slots.sql`) before the entity will validate successfully at boot.

## Frontend: pages own their own state, one axios client

`App.jsx` is the single source of truth for the route table and page-level access control — there's no separate router config file. Auth (`{ token, fullName, email, role }`) lives in a plain `useState` in `App`, hydrated once from `localStorage` (`getStoredAuth()`) and passed down as a prop; there's no context or Redux for it. Three guard components (`components/layout/RouteGuards.jsx`) compose route-level access: `ProtectedRoute` (any signed-in user), `PublicOnlyRoute` (signed-out only, e.g. the auth pages), and `RoleRoute` (signed-in and role in an allow-list, always redirecting to `/dashboard` rather than another gated route, to avoid redirect loops).

Every backend call goes through `src/api/apiClient.js` — a single axios instance (`baseURL: '/api'`) exporting one named function per endpoint, grouped by resource. A request interceptor attaches `Authorization: Bearer <token>` from `localStorage`; a response interceptor clears stored auth on any `401` so an expired/revoked session drops the user back to sign-in rather than looping on stale auth. Pages call these functions directly — there's no hook layer, no React Query/SWR, no shared cache. Each page manages its own `useState`/`useEffect` for its data and its own inline try/catch around API calls, surfacing errors via either an `error-banner` div (page load failures) or the toast system (`components/layout/ToastProvider.jsx`, mutation feedback).

Signed-out pages (auth, forgot/reset password) render standalone with no chrome. Signed-in pages get a `Sidebar` + `TopBar` shell around the routed content. `SiteHeader.jsx` (the pre-sidebar top-nav) and `SkillCatalogPage.jsx` exist in the tree but are intentionally unwired — legacy code left in place rather than deleted; don't assume everything under `pages/` is reachable from `App.jsx`'s route table.

## Authentication and request flow

There is no `UserDetailsService`-backed Spring Security login flow here. `AuthService` issues an opaque random token (`SecureRandom` + base64) into a `user_sessions` row on sign-in/sign-up; every subsequent request carries that token as a bearer header, and `TokenAuthenticationFilter` validates it against `user_sessions` on every request, building a Spring Security `Authentication` with a single `ROLE_<UserRole>` authority. `SecurityConfig` disables `formLogin`/`httpBasic` and replaces Spring Security's default unauthenticated-request handling (which redirects to an auto-generated `/login` page) with plain JSON 401/403 bodies — necessary because that redirect behavior breaks CORS for a JS frontend. Full detail in `security.md`.

## Interview workflow as a server-enforced state machine

`InterviewStatus` transitions (`SCHEDULED → IN_PROGRESS → SUBMITTED → RECOMMENDED → CLOSED`, plus a `CANCELLED` escape hatch) are validated against an explicit `ALLOWED_TRANSITIONS` map inside `InterviewService`, and status can only change through `PATCH /api/interviews/{id}/status`, never through a plain `PUT` — `applyFields()` only honors an incoming `status` field on *create*, specifically so a `PUT` can't bypass transition rules on an existing interview. Cancelling an interview that came from a booked slot releases that slot back to `AVAILABLE`.

## Cross-cutting concerns

**Auditing** is two independent mechanisms and they're not interchangeable: `AuditableEntity` + `JpaAuditingConfig`/`CurrentUserAuditorAware` passively stamp `created_at`/`updated_at`/`created_by`/`updated_by` on every entity that extends it, no code required per-entity; `AuditService.record(...)` is an explicit, service-level call written into `audit_logs` for domain actions (CREATE/UPDATE/DELETE/STATUS_CHANGE/SCHEDULE) — a new mutating action needs this call added by hand, it does not happen automatically. Both derive "who did this" from `CurrentUser.emailOrSystem()`, which reads `SecurityContextHolder` and falls back to `"system"` when there's no authenticated user.

**Notifications** are behind a `NotificationService` interface. `LoggingNotificationService` (console log only) is active by default so the app boots with zero mail configuration; setting `app.mail.enabled=true` switches in `EmailNotificationService` (Spring Mail / Gmail SMTP). A failed send is logged as an error but never blocks the underlying operation — a password-reset token is still created even if the email fails to send.

**File storage** (`FileStorageService`) is interface-shaped so local disk (today) can be swapped for S3 later without touching callers. Uploaded files are renamed to a random UUID plus the original extension, content-type allowlisted (PDF/PNG/JPEG/DOC/DOCX), and size-capped via `app.file-storage.max-size-bytes`.

**Reporting** (`ReportService`/`ReportController`) aggregates in memory over already-loaded entities rather than pushing aggregation into SQL — an accepted tradeoff at current data volume per the root README, to be revisited if interview volume grows into the tens of thousands.

**Error handling** is centralized in one `GlobalExceptionHandler`: `ResourceNotFoundException` → 404, `BadRequestException`/`IllegalArgumentException` → 400, `BadCredentialsException` → 401, `AccessDeniedException` → 403, bean-validation failures → 400 with field-level messages joined into one string, everything else → 500 with a generic message. New exception types should be added to this one handler rather than creating a second one.

## Deployment topology

`docker-compose.yml` runs three services: `mysql` (image `mysql:8.0`, auto-seeded from `database/schema.sql` via `docker-entrypoint-initdb.d` — **only on a fresh volume**, re-running Compose against an existing `db_data` volume does not re-apply `schema.sql`), `backend` (two-stage Maven build → `eclipse-temurin:17-jre-alpine`, port 8080, uploaded files on a named volume `uploads_data`), and `frontend` (multi-stage `npm install && npm run build` → nginx serving `dist/`, proxying `/api/` to `backend`, port 8081 externally). CORS origins for the Compose topology are widened to include `http://localhost:8081` (the nginx-served frontend) alongside the Vite dev ports.
