# 02 TRD: Technical Requirements

| Item | Decision |
|---|---|
| Frontend | React 18, plain JavaScript, Vite, React Router, Axios. Plain CSS from `design/design-system.css`. |
| Backend | Java 17, Spring Boot 3.3.x, Maven (wrapper), Spring Web, Spring Security, Spring Data JPA, JdbcTemplate |
| Database | MySQL 8 (installed locally or portable ZIP; no Docker). Schema from `db/schema.sql`, applied manually. JPA `ddl-auto=validate`. Must stay portable to PostgreSQL. |
| Auth | Spring Security, stateless JWT. Email + password only. |
| Hosting | Local development first: API `http://localhost:8080`, web `http://localhost:5173` |
| Third-party services | None |
| Key libraries | spring-boot-starter-web, -security, -data-jpa, -jdbc, -validation, -oauth2-resource-server, mysql-connector-j. Frontend: react, react-dom, react-router-dom, axios. Nothing else. |

## Environment variables (names only)
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (32+ characters), `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `APP_FRONTEND_URL` (used to build share links, default `http://localhost:5173`), `COOKIE_SECURE` (false in dev).

## Auth design
- Access token: JWT HS256, 15 minutes, claims `sub` (user id), `role`, `email`. Sent as `Authorization: Bearer`. Kept in memory in the SPA only.
- Refresh token: random 256-bit value, 7 days, stored hashed (SHA-256) in `refresh_tokens`, sent in an httpOnly cookie `assay_rt` (path `/api/auth`, SameSite=Lax, Secure from `COOKIE_SECURE`). Rotated on every refresh (old one revoked). Logout revokes it.
- Roles: `ADMIN`, `RESPONDENT`. The first admin is created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` if no admin exists. Respondents self-register. Passwords: BCrypt, minimum 8 characters.
- Login throttling: simple in-memory limiter, 5 failed attempts per email+IP per 15 minutes.
- Axios: one instance with `baseURL: '/api'`, `withCredentials: true`. A request interceptor adds the bearer token. A response interceptor on 401 calls `/auth/refresh` once (shared promise for parallel calls), retries the request, and on failure redirects to `/login`. On app load call `/auth/refresh` to restore the session.
- Vite dev proxy forwards `/api` to `http://localhost:8080`, so no CORS issues. Still configure CORS for `APP_FRONTEND_URL`.

## Conventions
- REST under `/api`, JSON, errors as `{ "status", "message", "fieldErrors": [] }`, ISO-8601 UTC timestamps, pagination only where lists can be large.
- Backend packages: `config`, `auth`, `user`, `form` (forms, versions, builder), `importer`, `submission`, `analytics`, `export`, `common`.
- Frontend: `src/api`, `src/auth`, `src/pages`, `src/components`, `src/styles/design-system.css`.
- Primary keys are BIGINT identity. The only public identifier is `forms.share_token`.
- Drag order: the client sends the full ordered list of ids; the server rewrites `sort_order`.

## CSV import (exact sample format)
Columns: `Q_ID, Section, Dimension, Question, Response_Type, Response_Options, Evidence_or_Example, Mandatory, Response, Comments`.
- Read with `MultipartFile`, UTF-8, strip BOM, accept CRLF, quoted fields with commas, doubled quotes, line breaks inside quotes. Detect comma or semicolon delimiter. Hand-written parser, no CSV library.
- `Response` and `Comments` are ignored.
- `Section` such as `A. Respondent & Application`: code `A`, name `Respondent & Application`. Sections and dimensions are created in order of first appearance. A dimension belongs to the section where it appears.
- `Response_Options` split on `|` and trimmed.
- `Response_Type` mapping: Text -> SHORT, Single choice -> SINGLE, Multi choice -> MULTI, Scale 1-4 -> SINGLE with scores, Number -> NUMBER.
- Scale 1-4 options look like `1 = Not in place`: score = the number, label = the text. An option named `Don't know` gets `is_na = true` and no score.
- Number options like `0-100 | Don't know`: `validation_json {"min":0,"max":100}` and `config_json {"allowDontKnow":true}`.
- `Mandatory`: `Y` or `N` (also accept yes, no, true, false).
- Validate everything first and collect all errors with row number and column: missing column, empty Q_ID, Section, Dimension or Question, duplicate Q_ID, unknown type, choice type with no options, bad Mandatory. If any error, save nothing and return the list.
- Save with `JdbcTemplate.batchUpdate` in one transaction: form (status DRAFT), version 1 (DRAFT), sections, dimensions, questions, options.

## Scoring
Only options with a score count. A scored question's score is the selected option's score; `Don't know` is excluded. Section and dimension scores are the average of the scored answers (range 1 to 4). Default target 3.0 (stored in `settings_json.target`). In the sample sheet only the 18 Scale 1-4 questions are scored.

## Constraints
No Docker. Java 17. Only the libraries listed above. Works on Chrome and Edge. The UI must follow `docs/04`.
