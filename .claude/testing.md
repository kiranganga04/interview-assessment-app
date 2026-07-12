# Testing

## What exists today

Backend tests live under `backend/src/test/java/com/interview/assessment/`, split the same way as production code: `service/` (`AuthServiceTest`, `InterviewServiceTest`) and `repository/` (`InterviewRepositoryTest`). The stack is JUnit 5 + Mockito for unit-level service tests (mocking repositories/collaborators, asserting behavior in isolation) and a real Spring Data JPA repository test backed by an in-memory H2 database in MySQL-compatibility mode for `InterviewRepositoryTest` — configured via `backend/src/test/resources/application.yml` (`jdbc:h2:mem:...;MODE=MySQL`, `ddl-auto: create-drop`). **No test ever touches the real MySQL instance** — running `mvn test` needs nothing but a JDK and Maven, no local database setup.

Run everything with `mvn test` from `backend/`; run a single class with `mvn test -Dtest=AuthServiceTest`; run a single method with `mvn test -Dtest=AuthServiceTest#signIn_ok`.

The frontend has **no test runner or lint script configured** in `package.json` — there is currently no automated frontend test coverage at all. `frontend/CLAUDE.md` confirms this explicitly.

## Coverage gaps worth knowing about

Only two backend services have unit tests (`AuthService`, `InterviewService`) out of the full set (`CandidateService`, `InterviewerService`, `InterviewSlotService`, `ReportService`, `SkillCatalogService`, `UserService`, `FileStorageService`, `AuditService`, the notification services are untested). Only one repository has a dedicated test (`InterviewRepository`). Controllers have no test coverage (no `@WebMvcTest`/MockMvc tests found) — request mapping, `@PreAuthorize` enforcement, and DTO (de)serialization at the HTTP boundary are currently only exercised manually (Swagger UI, Postman) or implicitly through the frontend. The frontend has zero automated tests of any kind — no component tests, no integration tests, no end-to-end tests.

The root README references a GitHub Actions CI workflow per repo (`backend/.github/workflows/ci.yml`, `frontend/.github/workflows/ci.yml`); as of this review neither `.github` directory exists in either repo, and `backend/CLAUDE.md` states plainly that there's no CI workflow file currently. Treat the README's CI claim as aspirational/outdated until a workflow file actually exists — don't assume tests are gated in CI today.

## Conventions to follow for new tests

**Service tests**: Mockito-style, mock the repository/collaborator dependencies, construct the service under test directly (no Spring context needed), assert on returned DTOs and on interactions with mocked collaborators (e.g. verifying `AuditService.record(...)` was called with the right action). Follow `AuthServiceTest`/`InterviewServiceTest`'s existing structure and naming (`methodName_scenario`, e.g. `signIn_ok`) for new test methods.

**Repository tests**: only worth adding when there's real query logic to verify (a custom `@Query`, a non-trivial derived query method, a JPA mapping subtlety) — point them at H2 via the existing `src/test/resources/application.yml`, following `InterviewRepositoryTest`'s pattern, rather than requiring MySQL.

**New services should get a unit test using the same Mockito pattern as the existing two** before being considered done — this repo's convention is clearly "service layer gets tests," even though it isn't fully applied yet; extending that coverage to `CandidateService`, `InterviewerService`, `InterviewSlotService`, etc. would bring the codebase in line with its own established pattern rather than introducing a new one.

## Recommended path forward (not yet implemented)

For the backend: fill in unit tests for the remaining services (especially `InterviewSlotService` and `InterviewerService`, which carry the newer scheduling/booking logic and the slot-release-on-cancel behavior), and add at least a thin `@WebMvcTest` layer per controller to verify `@PreAuthorize` role restrictions are actually wired correctly — this is the one thing unit-testing the service layer alone can't catch, since the authorization check lives on the controller method annotation.

For the frontend: introduce a test runner (Vitest is the natural fit given Vite is already the build tool) starting with the components most reused across pages (`RatingBadge`, `SkillAssessmentTable`, `CodingRoundTable`, the three `RouteGuards`) before attempting full page-level tests, since those guard components directly encode the access-control logic described in `security.md` and `ui-guidelines.md` and are cheap to test in isolation.

For CI: if automated checks matter going forward, add the two GitHub Actions workflow files the README already describes (`backend/.github/workflows/ci.yml` running `mvn test`, `frontend/.github/workflows/ci.yml` running `npm install && npm run build` plus whatever test command gets introduced) — right now nothing enforces that `mvn test` or a frontend build actually passes before code lands.
