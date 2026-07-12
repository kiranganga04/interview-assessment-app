# API Guidelines

## Conventions observed

Every endpoint lives under `/api/**` except `/actuator/health` and the Swagger/OpenAPI paths (`/swagger-ui.html`, `/v3/api-docs`) — these are the only routes that `permitAll()` alongside `/api/auth/**`; everything else requires a valid bearer session. There is no API version prefix (no `/api/v1/...`) — the API is currently unversioned.

Resources are plural, kebab-case where multi-word (`/api/candidates`, `/api/interviews`, `/api/interview-slots`, `/api/interviewers`, `/api/skills`, `/api/users`, `/api/files`, `/api/reports`), following standard REST verb/resource conventions: `GET` for reads (collection or `/{id}`), `POST` for create, `PUT` for full update, `PATCH` for a partial/targeted change (only used for the interview status transition, `PATCH /api/interviews/{id}/status`), `DELETE` for delete. Follow this pattern for new resources rather than introducing RPC-style action endpoints — the one exception already in the codebase is `POST /api/interview-slots/{id}/cancel`, a state-change action modeled as a `POST` sub-resource because slot cancellation isn't a full-resource update.

Request bodies are JSON (`Content-Type: application/json`) except file upload, which is `multipart/form-data` (`POST /api/files/{ownerType}/{ownerId}`). Response bodies omit null fields (`spring.jackson.default-property-inclusion: non_null` in `application.yml`) — don't rely on a field being present-but-null in a JSON response; check for absence.

## Auth

Every request other than `/api/auth/**` needs `Authorization: Bearer <token>`, where `<token>` came from a prior `POST /api/auth/signin` or `/signup` response and is validated per-request against the `user_sessions` table (not a JWT — it's an opaque, server-side-checked token, so revoking it server-side, e.g. via `/api/auth/logout`, takes effect immediately on the next request). A `401` on any endpoint means the token is missing, expired, or revoked; the frontend's response interceptor treats any `401` as "sign the user out locally."

## Selected endpoints (see Swagger UI at `/swagger-ui.html` or `postman_collection.json` for the full, current list)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/signup`, `/signin`, `/logout` | Account + session lifecycle. First-ever signup becomes `ADMIN`. |
| POST | `/api/auth/password-reset/request`, `/confirm` | Always succeeds from the caller's perspective even if the email send fails. |
| GET/POST/PUT/DELETE | `/api/candidates` | Candidate CRUD; `GET` supports `?name=` search. |
| GET/POST/PUT/PATCH/DELETE | `/api/interviews` | `GET` supports `?page=&size=&level=&status=&search=&candidateId=`; `PATCH /{id}/status` is the only way to change workflow status. |
| POST | `/api/interviews/schedule` | Books an `AVAILABLE` interview slot instead of free-typing panel/date/time. `ADMIN`/`RECRUITER` only. |
| GET/POST/PUT/DELETE | `/api/interviewers` | Bookable interviewer directory. `ADMIN`/`RECRUITER` only. |
| GET/POST/PUT | `/api/interview-slots`, `POST /{id}/cancel` | Slot scheduling. `ADMIN`/`RECRUITER` only. |
| GET/POST/PUT/DELETE | `/api/skills` | Skill catalog; `GET /all` includes inactive skills; writes are admin-only. |
| GET/PUT | `/api/users` | Admin user directory + role/active-flag updates. `ADMIN` only. |
| POST/GET | `/api/files/{ownerType}/{ownerId}` | Upload / list attachments; `GET /api/files/{id}` downloads a specific attachment. |
| GET | `/api/reports/summary`, `/pass-rate`, `/skill-averages`, `/panelist-calibration`, `/monthly-interviews`, `/today-agenda` | Dashboard/analytics data, read-only. |

`InterviewDTO` is the one payload shape worth calling out specifically: creating or updating an interview sends the whole assessment form in one request — `internalSkillAssessments`, `clientSkillAssessments`, and `codingRounds` all nested inside the same body — rather than separate calls per sub-section. A `PUT` does a full replace of both skill-assessment collections and the coding-round collection (clear + rebuild from the DTO); it does not diff/merge, so IDs on existing child rows are not preserved across an update. Keep this in mind both when calling the API and when extending it — a client that only sends a subset of skill assessments on `PUT` will delete the rest.

## Pagination

List endpoints that support it (`/api/interviews` today) accept `page` and `size` query params and return a `PageResponse` envelope rather than a bare array — expect `content`, plus paging metadata, in the response body. Follow this same envelope for any new paginated endpoint instead of inventing a second shape.

## Error format

Errors come back as a single, consistent `ApiError` JSON shape from `GlobalExceptionHandler`, mapped by exception type:

| Status | Cause |
|---|---|
| 400 | `BadRequestException`, `IllegalArgumentException`, or bean-validation failure (message is the joined field-level validation errors) |
| 401 | `BadCredentialsException` (bad sign-in) or an invalid/expired/missing session token |
| 403 | `AccessDeniedException` — authenticated, but the role lacks permission for this endpoint (`@PreAuthorize` failure) |
| 404 | `ResourceNotFoundException` — the path references an entity that doesn't exist |
| 500 | Anything else, with a generic message — never leak exception internals to the client |

A new endpoint should throw the existing exception types rather than returning a raw error body or a different shape — `GlobalExceptionHandler` is the single place error responses are shaped, and it should stay that way.

## Documentation

Swagger UI is live at `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`) whenever the backend is running — treat it as the current source of truth for exact request/response shapes, since it's generated from the live controller/DTO code rather than hand-maintained. `postman_collection.json` at the repo root is a smaller, hand-curated set of example requests (currently covering Candidates and Interviews) useful for quick manual testing; it is not exhaustive and can drift from the live API, so prefer Swagger for anything not covered there.

## CORS

Allowed origins come from `app.cors.allowed-origins` (`CorsConfig`, consumed by `SecurityConfig`), comma-separated and environment-overridable (`APP_CORS_ALLOWED_ORIGINS`). Local dev widens this to cover Vite's auto-incrementing ports (5173–5176) so a stray leftover `npm run dev` process doesn't produce a confusing CORS failure; the Docker Compose topology additionally allows the nginx-served frontend origin (`:8081`). Add a new frontend origin here, not by relaxing CORS globally, when standing up another environment.
