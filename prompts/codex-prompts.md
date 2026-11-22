# Codex prompts for Assay (run them in order, one per task)

**Before prompt 1:** put this whole folder in the repo root (`AGENTS.md`, `docs/`, `design/`, `db/`, `reference/`, `sample/`). Install JDK 17, Node 18+, and MySQL 8. Create the database `assay` and run `db/schema.sql` once.
Every prompt below implicitly includes: "Also read `docs/07-API-Contract.md` and use its paths and JSON field names exactly, then run the matching lines of `docs/08-Acceptance-Checklist.md` and report pass or fail." Paste each prompt as a separate task. Start the next one only after the "Done when" check passes. If something fails, use the Fix prompt at the bottom.

---
## Prompt 1: Project setup
Read `AGENTS.md` and all files in `docs/`. Create `backend/` (Spring Boot 3.3, Java 17, Maven wrapper, packages from docs/02) and `frontend/` (Vite + React + JavaScript, React Router, Axios). Configure `application.yml` from the env vars in docs/02 (JPA `ddl-auto=validate`), a `GET /api/health` endpoint, Vite proxy `/api` -> `http://localhost:8080`, and copy `design/design-system.css` to `frontend/src/styles/design-system.css`, imported in `main.jsx`. Frontend home page shows only the `assay.` brand using the `.brand` class. Add a short `README.md` with run steps for Windows.
**Done when:** both apps start, `/api/health` returns ok through `http://localhost:5173/api/health`, and the brand renders in Familjen Grotesk with the accent dot.

## Prompt 2: Auth backend
Implement authentication exactly as in docs/02 (Auth design) and docs/05: seeded admin from env, `POST /api/auth/register` (respondent only), `/login`, `/refresh` (rotation, httpOnly cookie), `/logout`, `GET /api/auth/me`, JWT resource-server config, role rules (`/api/forms/**` and admin routes need ADMIN; `/api/public/**` and `/api/auth/**` are public), the in-memory login throttle, and the standard error JSON. Write MockMvc tests for: login success and failure, refresh rotation and reuse of an old token, respondent calling an admin route gets 403, no token gets 401.
**Done when:** `./mvnw test` passes and the tests above exist.

## Prompt 3: Auth frontend
Create `src/api/axios.js` (instance, bearer interceptor, single shared refresh on 401, redirect to `/login` on failure, refresh on app load), `AuthContext`, route guards by role, `/login` and `/register` pages using `.auth-layout` and `.auth-box` like `reference/assay-ui-reference.html`, and the admin and respondent top bars (`.topbar`, `.topbar__tab`). Handle `returnTo`. Stub `/admin/forms` and `/fill/:id` pages with the page title only.
**Done when:** admin login lands on `/admin/forms`; a respondent who registers is sent back to `returnTo`; refreshing the browser keeps the session; wrong role is redirected.

## Prompt 4: Forms table
Backend: `GET /api/forms/overview`, `POST /api/forms` (blank, with title), soft delete. Frontend: the Forms table exactly as described in docs/04 (Forms table) and the reference file: only the right-aligned `.btn-solid--xl` "+ New form" and the table with all columns, line bar, 10-block meter, stacked bar and status dots. New form opens a small `.drawer` with title and a choice **Start blank** or **Import CSV** (file input). Row "Open" goes to builder for DRAFT/SAVED, responses otherwise. Use seed data through the API only; no fake data in the frontend.
**Done when:** creating a blank form shows a new row; the table matches the reference style.

## Prompt 5: CSV import
Implement the importer exactly as in docs/02 (CSV import) using a hand-written parser and `JdbcTemplate.batchUpdate` in one transaction. Endpoint `POST /api/forms/import` (multipart: `title`, `file`). Return all validation errors with row and column and save nothing if any. Frontend: show the error list in the drawer, or navigate to the builder on success. Write unit tests with `sample/QE_Assessment_PreRead_Questionnaire_App_Leads.csv` (expect 11 sections, 22 dimensions, 72 questions, 53 mandatory, 18 scored questions with options scored 1 to 4 and "Don't know" flagged is_na) and with broken files (missing column, duplicate Q_ID, empty question, bad type).
**Done when:** the sample imports with the expected counts and the tests pass.

## Prompt 6: Builder
Backend: `GET /api/forms/{id}/builder` and the section, dimension and question CRUD, clone, and the three reorder endpoints from docs/05. Frontend: the builder screen exactly like `reference/assay-ui-reference.html` and docs/04 (Builder): left Sections list with "+" and delete, Dimensions under the selected section, question rows with `.handle`, inline `.q-editor`, the custom `.dd` dropdown for Question type (15 types with icons) and Validation, options with add and remove, evidence hint, `.switch` for Mandatory, Clone, Delete, Close, "+ Add question", Save draft. Drag and drop for sections, dimensions and questions with the FLIP slide animation from the reference (reorder DOM during dragover, persist order on drop). Build the dropdown and switch as reusable components that only use design-system classes.
**Done when:** every control works and persists after reload; reordering animates smoothly; no native select or new colors were introduced.

## Prompt 7: Publish and versioning
Backend: publish (freeze draft, archive previous, create `share_token` if missing, expiry and optional opens_at), regenerate, unpublish, new-version (clone the tree into a new draft), `GET /api/public/forms/{token}`. Link base from `APP_FRONTEND_URL`. Frontend: the Publish `.drawer` with expiry date-time, copy field, Regenerate, Open as respondent; the public page `/f/:token` with the states in docs/03; builder shows "v4 draft" style version text and creates a new version when a published form is edited.
**Done when:** the copied link opens the landing page; revoke and expiry show the right messages; old responses stay on their version.

## Prompt 8: Respondent flow
Backend: `POST /api/fill/{token}/start` (create or resume), `GET /api/submissions/{id}`, `PUT /api/submissions/{id}/answers` (batch, `If-Match` row version, expiry check, state ANSWERED/REVIEW/DRAFT), `POST /api/submissions/{id}/submit` (server-side mandatory and validation checks), edit after submit until expiry, `submission_events`. Frontend: `/fill/:id` exactly like the reference respondent screen and docs/04 (Respondent): segmented progress, section index with dot matrix and `8/12 · 4 left`, legend counts, "Show all questions in this section" switch, all question types rendered, Mark for review, Save as draft, debounced autosave (about 800 ms), review screen, done page, edit again.
**Done when:** answers survive a reload; a second Start returns the same submission; submit is blocked with missing mandatory answers; after expiry the screen is read-only.

## Prompt 9: Responses
Backend: responses list, one submission detail, by-question answers, scores (average of scored answers per dimension and section, target from settings), wide and long CSV for all, per-person CSV (with the CSV injection guard). Frontend: Responses page exactly as in docs/04 (Responses): tabs, respondents table, person detail with key-value strip and Download CSV, by-question table with the section filter dropdown and expandable per-respondent answers, Scores tab with score rows and heat strip, Export all wide and long.
**Done when:** with two respondents who submitted, every number matches the database and the CSVs open correctly in Excel.

## Prompt 10: Hardening and assignments
Add all empty, loading and error states from docs/03, form-level validation messages, a 404 page, and the optional assignments feature (paste emails in the Publish drawer; restricts who can start; fills Assigned and Not responded in the Forms table). Update the README with setup, env variables and how to test. Run a final check against `AGENTS.md` (stack and design locks) and report any deviation.
**Done when:** every flow in `docs/03-App-Flow.md` passes manually.

---
## Change request prompt (use this when adding or removing a feature)
Read `AGENTS.md` and `docs/04-UI-UX-Design-Brief.md`. Change request: <describe it>. Reuse existing components from `design/design-system.css`. If a new component is unavoidable, add it to the style sheet in the same visual language and list it in docs/04 first. Do not add libraries, colors, fonts, shadows or cards. Update the affected docs and `db/schema.sql` only if the data model changes, and list files changed.

## Fix prompt
Read `AGENTS.md`. The problem: <what you see, the page, the steps, the error text>. Find the root cause, fix it with the smallest change, add or update a test if it is backend logic, and do not change the design.
