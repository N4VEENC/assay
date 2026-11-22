# 05 Backend Schema, Auth and API

SQL: `db/schema.sql` (13 tables). Hierarchy: **Form -> Version -> Section -> Dimension -> Question -> Option**.

## Tables
| Table | Purpose |
|---|---|
| users | Admins and respondents (email, password_hash, role ADMIN or RESPONDENT) |
| refresh_tokens | Hashed rotating refresh tokens |
| forms | One per form: title, status, share_token, link_status, published_version_id |
| form_versions | DRAFT / PUBLISHED / ARCHIVED snapshots with opens_at, expires_at, settings_json |
| sections | Chapters (CSV Section column) of a version |
| dimensions | Subject groups inside a section (CSV Dimension column) |
| questions | Text, type, mandatory, evidence hint, validation_json, config_json, order |
| question_options | Choices with optional score and is_na flag |
| form_assignments | Optional invited emails (enables Assigned counts) |
| submissions | One per respondent per form, pinned to a version, UNIQUE(form_id, user_id) |
| answers | One per question per submission, with state ANSWERED / REVIEW |
| answer_options | Selected options for MULTI and RANKING answers |
| submission_events | Created, saved, submitted, edited history |

## Relationships
forms 1-N form_versions; form_versions 1-N sections 1-N dimensions; questions belong to a version, a section and a dimension; questions 1-N question_options; forms 1-N submissions N-1 users; submissions 1-N answers; answers N-N question_options via answer_options.

## Roles and access rules
| Action | ADMIN | RESPONDENT | Public |
|---|---|---|---|
| Register | | yes (own account) | |
| Login, refresh, logout | yes | yes | |
| Create, edit, import, publish, delete forms | yes | no | |
| See forms table, responses, analytics, exports | yes | no | |
| Read share link info `/public/forms/{token}` | | | yes |
| Start, save, submit own submission | preview only | yes (own only) | |

Every query that touches forms is restricted to `owner_id = current admin`. Every submission query is restricted to `user_id = current respondent`. Return 404 for rows that belong to someone else.

## Form status
Stored: `DRAFT` (new or being edited, never saved), `SAVED` (admin pressed Save draft), `PUBLISHED`, `CLOSED` (link revoked). Displayed: `OPEN` = published and now between opens_at and expires_at; `PUBLISHED` = published but not yet open; `EXPIRED` = past expires_at.

## Forms table counts
Assigned = rows in form_assignments (if none, show "open"). Responded = submissions of the form (any status). Not responded = assigned - responded. Submitted = submissions with SUBMITTED. Not submitted = assigned - submitted. Completion = submitted / assigned; for open forms with no assignments show submitted count only.

## Endpoints
Auth: `POST /api/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/auth/me`.

Admin forms: `GET /api/forms/overview`, `POST /api/forms` (blank), `POST /api/forms/import` (multipart: title + file), `GET/PUT/DELETE /api/forms/{id}`, `GET /api/forms/{id}/builder` (full tree of the draft), `POST /api/forms/{id}/save-draft`.

Builder: `POST/PUT/DELETE /api/forms/{id}/sections`, `/dimensions`, `/questions`; `POST /api/questions/{id}/clone`; `PUT /api/forms/{id}/sections/order`, `/dimensions/order`, `/questions/order` (body: ordered ids).

Publishing: `POST /api/forms/{id}/publish` (body: expiresAt, opensAt) returns the link; `POST /api/forms/{id}/link/regenerate`; `POST /api/forms/{id}/unpublish`; `GET /api/forms/{id}/versions`; `POST /api/forms/{id}/new-version` (clone published tree into a new draft).

Public and respondent: `GET /api/public/forms/{token}`; `POST /api/fill/{token}/start`; `GET /api/submissions/{id}` (questions, saved answers, states, progress); `PUT /api/submissions/{id}/answers` (batch of changed answers, header `If-Match: rowVersion`); `POST /api/submissions/{id}/submit`.

Responses (admin): `GET /api/forms/{id}/responses`, `GET /api/forms/{id}/submissions/{sid}`, `GET /api/forms/{id}/questions/{qid}/answers`, `GET /api/forms/{id}/scores`, `GET /api/forms/{id}/export?format=wide|long`, `GET /api/forms/{id}/submissions/{sid}/export`.

## Validation and rules
- Save and submit are rejected after `expires_at` (410) and when the link is revoked.
- Submit re-validates every mandatory question and each question's validation_json on the server. Unscored and unanswered questions are allowed only if not mandatory.
- A second `start` returns the existing submission. The unique constraint is the final guard.
- Editing after submit is allowed until expiry; each edit writes `submission_events`.
- Sensitive data: password_hash (BCrypt) and refresh token hashes only. No plain secrets stored.
