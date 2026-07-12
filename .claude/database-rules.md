# Database Rules

## Ownership and source of truth

The schema is hand-written SQL (`database/schema.sql`), not JPA/Hibernate-generated — the backend's `ddl-auto` is `validate`, meaning Hibernate checks the entity mappings against the existing schema at boot and fails loudly if they disagree, but it never creates or alters tables itself outside of tests. **Any schema change starts in `schema.sql` (or an additive migration file), not in a `@Entity` class.** Engine is `InnoDB` throughout; character set is `utf8mb4` / `utf8mb4_unicode_ci` at the database level.

`schema.sql` is only auto-applied by Docker Compose on the *very first* startup of a fresh `db_data` volume (`docker-entrypoint-initdb.d` scripts don't re-run against an already-initialized volume) and it does a `DROP DATABASE IF EXISTS` first — running it against a live database wipes existing data. For a database that already has data, ship an additive `migration_vN_*.sql` file instead (see `migration_v2_interview_slots.sql` for the pattern: `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN` for new nullable columns, then add the FKs/indexes afterward) and apply it by hand (`docker exec -i <mysql-container> mysql -uroot -p<pw> <db> < migration.sql` or any MySQL client). Never edit `schema.sql` alone and assume an already-running database picks it up.

## Naming conventions

Tables: lowercase, snake_case, plural (`candidates`, `interviews`, `skill_assessments`, `coding_rounds`, `interview_slots`, `user_sessions`, `password_reset_tokens`, `audit_logs`, `attachments`). Primary keys: `<singular_table>_id` (e.g. `candidate_id`, `interview_id`, `skill_assessment_id`), `BIGINT AUTO_INCREMENT`. Foreign keys: same name as the referenced primary key (`candidate_id` in `interviews` referencing `candidates.candidate_id`) with an explicit named constraint, `fk_<child>_<parent-ish>` (`fk_interview_candidate`, `fk_slot_interviewer`). Unique constraints: `uk_<table>_<column>` (`uk_app_users_email`, `uk_skills_name`, `uk_slot_code`). Indexes: `idx_<table>_<column(s)>` (`idx_interview_status`, `idx_skill_interview`, `idx_slot_date_status`).

## Status / fixed-value columns

Fixed value sets are modeled as MySQL `ENUM` columns, matched one-to-one with a Java enum in `entity/` — `app_users.role` (`ADMIN`/`RECRUITER`/`PANEL`), `interviews.status` (`SCHEDULED`/`IN_PROGRESS`/`SUBMITTED`/`RECOMMENDED`/`CLOSED`/`CANCELLED`), `interviews.level_of_interview` (`L1`/`L2`/`L3`/`HR`/`CLIENT`), `interviews.mode_of_interview` and `interview_slots.mode` (`VIRTUAL`/`IN_PERSON`/`TELEPHONIC`), `interview_slots.status` (`AVAILABLE`/`BOOKED`/`CANCELLED`), `skill_assessments.panel_type` (`INTERNAL`/`CLIENT`), `coding_rounds.test_complexity` (`LOW`/`MEDIUM`/`HIGH`), `coding_rounds.coding_status` (`COMPLETED`/`NOT_COMPLETED`), `attachments.owner_type` (`CANDIDATE_RESUME`/`INTERVIEW_SCREENSHOT`). Widening one of these enums (adding a new value) is an additive `ALTER TABLE ... MODIFY COLUMN` and is safe against existing rows, as done for `interviews.status` in `migration_v2_interview_slots.sql`; narrowing one is not something this codebase does and would need a data migration first.

There is also a small static lookup table, `rating_scale` (`rating_value` 1–5 → `rating_label`), seeded once and not written to by the application — treat it as reference data, not a table the app CRUDs.

## Auditing columns

`created_at` / `updated_at` (`TIMESTAMP DEFAULT CURRENT_TIMESTAMP` / `... ON UPDATE CURRENT_TIMESTAMP`) appear on most tables and are populated automatically by MySQL's own timestamp defaults *and* redundantly by the JPA auditing layer on the entity side — this is fine, it's belt-and-suspenders, don't remove either. `created_by` / `updated_by` (`VARCHAR(150)`, storing an email or `"system"`) appear on `candidates` and `interviews` specifically (the two entities that extend `AuditableEntity`) — add these two columns to a new table only if the corresponding entity will extend `AuditableEntity`.

## Ratings

Rating columns (`communication_rating`, `final_rating`, `self_rating`, `rating`) are `DECIMAL(3,1)`, deliberately to support half-point scores like 3.5 — don't change these to integer types.

## Polymorphic ownership

`attachments` uses `owner_type` + `owner_id` rather than a separate foreign-key column per possible owner (`candidate_id`, `interview_id`, etc.) — one table serves resumes on candidates and screenshots on interviews alike. If a third attachment owner type is added later, extend the `owner_type` enum and reuse this table rather than creating a parallel one. The same one-table-two-purposes pattern applies to `skill_assessments`: one table backs both the internal panel section and the client technical panel section of the assessment form, discriminated by `panel_type` rather than two separate tables with identical columns.

## Nullability choices worth knowing before you change them

`interviews.interviewer_id` and `interviews.slot_id` are nullable by design — they were added alongside the interviewer/slot scheduling feature, and making them non-null would break every interview created via the plain free-text form (`panel_member_name` only, no interviewer/slot linkage), which remains a valid, independent creation path. Don't tighten these to `NOT NULL` without also removing the free-text creation path.

## Indexes already in place

`idx_interview_candidate`, `idx_interview_status`, `idx_interview_interviewer`, `idx_interview_slot` on `interviews`; `idx_skill_interview` on `skill_assessments(interview_id, panel_type)`; `idx_coding_interview` on `coding_rounds(interview_id)`; `idx_candidate_name` on `candidates(candidate_name)`; `idx_user_sessions_user`, `idx_app_users_email`; `idx_slot_interviewer`, `idx_slot_date_status` on `interview_slots`. Follow this pattern for new tables: index every foreign key used in a lookup, plus any column used as a filter in a list endpoint (status, date, search-by-name columns).

## Seed / demo data

`schema.sql` seeds three demo accounts, one per role, all sharing the password `Password123!` (bcrypt-hashed in the script, verified against Spring Security's `BCryptPasswordEncoder`), plus 19 candidates/interviews spanning every level and workflow status so the dashboard and report views have real numbers immediately after setup, and a small interviewer/slot directory. These are local/dev convenience data only — never point this script at anything but a local database, and rotate or remove the demo accounts before any shared/hosted environment.

## Local secrets

Local-only credential overrides (currently SMTP username/password for real password-reset email) live in `backend/application-local.yml`, which is gitignored and loaded automatically via `spring.config.import: optional:file:./application-local.yml` — never put real credentials into the tracked `application.yml`, and never commit `application-local.yml`. A live-looking Gmail App Password was present in this file on disk at review time; treat that file as sensitive even though it's gitignored, and rotate the credential if this repository or its contents are ever shared outside the local machine.
