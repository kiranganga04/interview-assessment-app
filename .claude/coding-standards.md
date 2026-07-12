# Coding Standards

These conventions are inferred from the existing codebase — the goal is for new code to be indistinguishable in style from what's already here, not to introduce a new convention on top of it.

## Backend (Java / Spring Boot)

**Package-by-layer, not package-by-feature.** Everything lives under `com.interview.assessment`, split into `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `security`, `service` — a new resource (say, "offers") gets a controller in `controller/`, a service in `service/`, DTOs in `dto/`, an entity in `entity/`, a repository in `repository/`, not a new `offers/` package containing all of those. Keep new code in the existing layer folders.

**One controller per REST resource**, named `<Resource>Controller`, thin — request mapping, `@PreAuthorize`, and delegation to a service method, no business logic in the controller itself. Controllers return DTOs, never entities directly.

**One service per resource**, named `<Resource>Service`, holding the actual business logic and the transaction boundary. Cross-cutting concerns get their own service (`AuditService`, `FileStorageService`, `NotificationService`) rather than being duplicated into each resource service — call the shared service instead of reimplementing the behavior.

**DTOs are always separate from entities.** Every entity that crosses the API boundary has a matching DTO (e.g. `Interview` entity / `InterviewDTO`), even when the shape looks identical today — this is what lets the entity evolve to match the database schema independently of what the frontend receives. `PageResponse` is the shared wrapper for any paginated list endpoint; reuse it rather than hand-rolling a new page envelope.

**Entities extend `AuditableEntity`** when they need `created_at`/`updated_at`/`created_by`/`updated_by` — don't add those four columns by hand to a new entity; extend the mapped superclass and let `JpaAuditingConfig` handle it. Enum-like fixed value sets (`InterviewStatus`, `InterviewLevel`, `InterviewMode`, `UserRole`, `PanelType`, `SlotStatus`, `CodingStatus`, `TestComplexity`, `AttachmentOwnerType`) are modeled as real Java enums matching a MySQL `ENUM` column, not free-text strings with validation bolted on elsewhere.

**Lombok is used for boilerplate** (`pom.xml` has it as an `optional` dependency, excluded from the fat jar). Follow the existing pattern of using Lombok annotations for getters/setters/constructors on entities and DTOs rather than hand-writing them.

**Mutating actions get an explicit `AuditService.record(...)` call** at the point of mutation in the service layer, in addition to whatever passive auditing `AuditableEntity` already provides — this is not automatic, so a new create/update/delete/status-change action needs this line added by hand (see `InterviewService` for the pattern of CREATE/UPDATE/DELETE/STATUS_CHANGE/SCHEDULE actions).

**State transitions belong in the service, validated against an explicit allow-list**, not scattered `if` checks across controllers — follow `InterviewService`'s `ALLOWED_TRANSITIONS` map pattern for any other entity that gains a workflow status.

**Exceptions:** throw `ResourceNotFoundException` for missing entities and `BadRequestException` for invalid input; let `GlobalExceptionHandler` do the HTTP status mapping. Don't add a new `@ExceptionHandler`-annotated class elsewhere — extend the one global handler so error shape stays consistent.

**No lint/format tool is configured** (no Checkstyle, no Spotless in `pom.xml`) — match the surrounding file's formatting by eye (4-space indentation, brace-on-same-line) rather than relying on a formatter to catch drift.

## Frontend (React / Vite)

**One file per page under `src/pages/`**, self-contained: local `useState` for form/list data, `useEffect` for the initial load, an inline `try/catch` around each API call. There is no shared data-fetching or caching library (no React Query, no SWR) — don't introduce one for a single page's needs; follow the existing local-state pattern.

**All backend calls go through `src/api/apiClient.js`.** Add a new named export grouped under the relevant `// ---- Resource ----` comment block rather than calling `axios` directly from a page or creating a second client instance. Function names follow the existing `verbNoun` convention (`listCandidates`, `createInterview`, `updateInterviewSlot`, `cancelInterviewSlot`).

**Shared display components live in `src/components/`** (not `pages/`) when used by more than one page — `RatingBadge`, `SkillAssessmentTable`, `CodingRoundTable` are the existing examples. Layout/shell components (`Sidebar`, `TopBar`, `SiteFooter`, `RouteGuards`, `ToastProvider`) live in `src/components/layout/`.

**Route access is enforced in two places that must be kept in sync**: the `RoleRoute roles={[...]}` prop in `App.jsx` and the `@PreAuthorize` on the matching backend controller method. When adding or changing a gated route or endpoint, update both — the frontend guard is a UX nicety, not the security boundary (that's the backend's job; see `security.md`).

**Mutations get toast feedback** (`useToast().success(...)` / `.error(...)`), **page-load failures get an inline `error-banner`** — follow this split rather than mixing the two or introducing a third pattern.

**Styling is one global stylesheet** (`src/index.css`), plain class names in a light BEM-ish convention (`card`, `data-card`, `metric-grid`, `status-chip status-{status}`, `sidebar-link`) plus CSS custom properties (e.g. `--ink-muted`). No CSS modules, no Tailwind, no styled-components/emotion — don't introduce a second styling approach for a new component; add classes to `index.css` following the existing naming.

**No test runner or lint script is configured** in `frontend/package.json` today — see `testing.md` for what this means for new frontend work.

## General

Comments in this codebase tend to explain *why*, not *what* — see the module-numbered comments throughout (`// module 4: ...`) that tie a piece of code back to the feature/README section that motivated it. Follow that habit for anything non-obvious: a one-line comment on why a design choice was made is more valuable here than a comment restating the code.
