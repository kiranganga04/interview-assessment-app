# UI Guidelines

## Shell and layout

Signed-out screens (sign in, sign up, forgot/reset password) render standalone — no sidebar, no top bar, just the page content and the footer (`SiteFooter`). Signed-in screens get the full shell: `Sidebar` on the left, `TopBar` plus routed page content on the right (`app-shell-sidebar` / `app-main` classes in `index.css`). This split happens once, in `App.jsx`, based on whether `auth` is set — a new page doesn't need to opt into the shell itself.

`Sidebar.jsx` builds its navigation as a declarative array of groups/items, each filtered by role flags derived from `auth.role`, then drops any group left empty after filtering. Adding a new nav entry means adding to that array, not hand-writing a new `<Link>` somewhere else in the tree. `SiteHeader.jsx` is legacy (the pre-sidebar top-nav) and is intentionally left in the tree but unused — don't wire it back up, and don't be surprised it exists if you're scanning `components/layout/`.

## Access control at the UI layer

Every route in `App.jsx`'s route table is wrapped in one of three guards from `RouteGuards.jsx`: `ProtectedRoute` for any signed-in user, `PublicOnlyRoute` for signed-out-only pages (redirects to `/dashboard` if already signed in), and `RoleRoute` for a specific role allow-list (redirects to `/dashboard`, never to another role-gated route, to avoid a redirect loop between two roles that each get bounced from the other's page). This is a UX convenience, not the security boundary — the real enforcement is the backend's `@PreAuthorize`, and the two must be kept in sync by hand whenever a route's access changes (see `security.md`).

## Data and forms

Pages are self-contained: local `useState` for whatever the page needs (form fields, list data, loading/error flags), `useEffect` to kick off the initial load, and an inline `try/catch` around each API call. There is no shared form library (no Formik/React Hook Form) and no shared data-fetching/caching layer — a new page follows this same local-state pattern rather than introducing a new one for itself.

Feedback on a **mutation** (create/update/delete/status change) goes through the toast system: `useToast()` from `ToastProvider.jsx`, calling `.success(msg)` / `.error(msg)` / `.info(msg)`, auto-dismissing after 4 seconds. Feedback on a **failed page load** goes through an inline `error-banner` div instead. Keep this split — don't toast a load failure or banner a mutation result.

## Shared display components

`RatingBadge` renders a 1–5 rating as a colored chip (`rating-badge rating-{1..5}`, or `rating-na` for null/undefined/empty), optionally with its label (`Exceptional`/`Good`/`Average`/`Below Avg`/`Poor`) — use this anywhere a rating value is displayed rather than re-implementing the color/label mapping. `SkillAssessmentTable` and `CodingRoundTable` render the repeating-row sections of the assessment form/detail views (skill rows, coding-round rows) and are shared between the form and detail pages — extend these rather than forking a page-local copy when the assessment form's shape changes.

## Styling

One global stylesheet, `src/index.css` — no CSS modules, no Tailwind, no styled-components. Class names follow a light BEM-ish convention already established in the file: block-level cards (`card`, `data-card`), layout helpers (`metric-grid`), state-driven modifiers (`status-chip status-{status}`, `rating-badge rating-{n}`), and navigation (`sidebar-link`). CSS custom properties (e.g. `--ink-muted`) hold shared design tokens like muted text color. A new component should add its classes to `index.css` following this same naming shape rather than introducing scoped/inline styles or a second stylesheet.

## Branding

Product name and tagline ("Interview Assessment System" / "Panel evaluation workspace") and footer link groups live in `src/config/navigation.js`, consumed by `Sidebar` and `SiteFooter`. This file was previously hotlinking a real company's branding and was deliberately replaced with original, neutral content — keep any future edits to this file generic to this project, not tied to any specific client or company.

## Known unwired pages

`SkillCatalogPage.jsx` exists in `src/pages/` but is not currently referenced from `App.jsx`'s route table — same treatment as `SiteHeader.jsx`. If you're asked to make the skill catalog reachable from the UI, that's a routing change (add it to `App.jsx` and to `Sidebar`'s nav array), not a new page to write.

## Responsive/accessibility notes

There's no dedicated design-system or component library in use (no MUI, no Chakra, no shadcn) — everything is hand-rolled HTML/JSX plus the global stylesheet. When adding new UI, match the existing semantic-HTML-first approach (real `<table>` for tabular data as in `SkillAssessmentTable`/`CodingRoundTable`, real `<form>`/`<button>` elements) rather than introducing a component library dependency for a single new screen.
