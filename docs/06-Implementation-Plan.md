# 06 Implementation Plan

Build in this order. Do not start a phase before the previous "done" check passes. The matching prompts are in `prompts/codex-prompts.md`. Exact API shapes are in `docs/07-API-Contract.md`; the test script for every phase is `docs/08-Acceptance-Checklist.md`.

| Phase | Goal | Done when |
|---|---|---|
| 1. Setup | Maven backend and Vite frontend skeletons, env config, MySQL connection with `db/schema.sql`, Vite proxy, design-system.css imported | `GET /api/health` returns ok; the React app shows the brand with the correct fonts and colors |
| 2. Auth backend | Seeded admin, respondent register, login, refresh rotation, logout, JWT filter, role rules, login throttle | Tests pass: login ok and fail, refresh rotates, admin-only routes reject respondents |
| 3. Auth frontend | Axios instance and interceptor, AuthContext, route guards, login and register pages, top bar | Admin lands on `/admin/forms`, respondent returns to `returnTo`, refresh works after 15 minutes |
| 4. Forms table | Overview endpoint, Forms table page, New form dialog (blank or import) | Table shows all columns with bars and a prominent New form button |
| 5. CSV import | Hand-written parser, validation with row errors, JdbcTemplate batch save | The sample CSV creates 11 sections, 23 dimensions, 72 questions; bad files return all errors |
| 6. Builder | Section, dimension, question CRUD; reorder with drag and FLIP; clone; delete; inline editor; 15 types; options; validation; mandatory; save draft | Every control in the reference builder works and persists |
| 7. Publish | Versioning, publish drawer, share link, expiry, regenerate, unpublish, new-version | Link opens the public landing page and honors expiry and revoke |
| 8. Respondent | Landing, start or resume, autosave, progress, section index with dot matrix, show-all toggle, review flags, review screen, submit, edit | Full cycle works; a second submission is impossible; mandatory rules enforced server-side |
| 9. Responses | Respondents table, person detail, by-question expansion, scores, CSV exports | All exports open in Excel; numbers match the database |
| 10. Hardening | Error and empty states, validation messages, rate limit, assignments (optional), README | All flows in `03-App-Flow.md` pass manually; no console errors |

## Done criteria for the whole app
- The sample CSV can be imported, edited, published, answered by two different respondents and analyzed by the admin.
- The UI matches `reference/assay-ui-reference.html` and uses only `design-system.css`.
- No library outside the stack lock in `AGENTS.md` was added.
