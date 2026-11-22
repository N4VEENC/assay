# 07 API Contract (exact JSON). If docs 05 and 07 disagree, **07 wins**.

Backend DTO fields and frontend usage must match these names exactly (camelCase). Do not rename, add or drop fields without updating this file.

## Conventions
- Base path `/api`. JSON only (except CSV export and CSV upload). Timestamps: ISO-8601 UTC strings, e.g. `"2026-12-31T12:30:00Z"`. Missing values are `null`, never omitted.
- Auth header: `Authorization: Bearer <accessToken>`. Roles: `/api/forms/**`, `/api/sections/**`, `/api/dimensions/**`, `/api/questions/**` need ADMIN. `/api/fill/**`, `/api/submissions/**` need RESPONDENT. `/api/public/**` and `/api/auth/**` are public (`/auth/me` needs any valid token).
- Error body for every failure: `{ "status": 400, "message": "text", "fieldErrors": [ { "field": "email", "message": "text" } ] }` (`fieldErrors` may be empty).
- Status codes: 400 invalid input, 401 not logged in or bad token, 403 wrong role or not allowed, 404 not found or not yours, 409 conflict (duplicate email, stale rowVersion), 410 closed or expired, 422 business validation (import, publish, submit), 429 login throttled.
- Enums. `role`: `ADMIN`, `RESPONDENT`. `storedStatus`: `DRAFT`, `SAVED`, `PUBLISHED`, `CLOSED`. `displayStatus`: `DRAFT`, `SAVED`, `PUBLISHED` (not open yet), `OPEN`, `EXPIRED`, `CLOSED`. `submissionStatus`: `DRAFT`, `SUBMITTED`. `answerState`: `ANSWERED`, `REVIEW`.
- `questionType`: `SHORT, LONG, SINGLE, MULTI, DROPDOWN, YES_NO, SCALE, RATING, LIKERT, NPS, RANKING, DATE, TIME, NUMBER, EMAIL`.
- **displayStatus rule:** stored `CLOSED` -> `CLOSED`. Stored `PUBLISHED` -> if now < opensAt `PUBLISHED`, if now > expiresAt `EXPIRED`, else `OPEN`. Otherwise the stored value.

## Auth
`POST /auth/register` `{ "name","email","password" }` -> 201 `{ "accessToken":"...", "user":{ "id":1,"name":"","email":"","role":"RESPONDENT" } }` and sets cookie `assay_rt`. 409 if email exists. Password min 8 characters.
`POST /auth/login` `{ "email","password" }` -> 200 same body as register. 401 message `Invalid email or password` (same for unknown email). 429 when throttled.
`POST /auth/refresh` (cookie + header `X-Requested-With: assay`) -> 200 same body. 401 if the cookie token is missing, expired, revoked.
`POST /auth/logout` -> 204 (revokes token, clears cookie). `GET /auth/me` -> `{ "id","name","email","role" }`.

## Forms
`GET /forms/overview` -> array of
`{ "id","title","versionNo","storedStatus","displayStatus","assigned":null|int,"responded":int,"submitted":int,"createdAt","expiresAt":null|ts }`.
`versionNo` is the published version number if any, else the draft's. `assigned` is `null` when the form has no assignments. Not responded, not submitted and completion are computed in the frontend from these numbers.
`POST /forms` `{ "title","description" }` -> 201 `{ "id" }` (creates the form, draft version 1, no sections).
`POST /forms/import` multipart (`title`, `file`) -> 201 `{ "id","sections":11,"dimensions":23,"questions":72 }`. On bad file -> 422 `{ "status":422,"message":"Import failed","fieldErrors":[],"importErrors":[ { "row":5,"column":"Response_Type","message":"Unknown type 'Foo'" } ] }` (row = line number in the file, header is row 1). The sample has separate `Evidence` and `Other` dimensions in section K.
`PUT /forms/{id}` `{ "title","description" }` -> 200 `{ "id","title","description" }`. `DELETE /forms/{id}` -> 204 (soft delete).
`POST /forms/{id}/save-draft` -> 200 `{ "storedStatus":"SAVED" }` (if the form is published it stays `PUBLISHED`; the draft version changes are kept).

## Builder (always works on the DRAFT version)
`GET /forms/{id}/builder` -> 200 `Builder`. If the form is published and has no draft, the server first clones the published tree into a new draft version (versionNo + 1), then returns it.
```
Builder = {
  "form":{ "id","title","description","storedStatus","displayStatus","shareToken":null|"x","link":null|"http://localhost:5173/f/x" },
  "version":{ "id","versionNo","status":"DRAFT","opensAt":null|ts,"expiresAt":null|ts,"settings":{ "target":3.0,"allowEditAfterSubmit":true } },
  "sections":[ { "id","code":"A","name","sortOrder",
      "dimensions":[ { "id","name","sortOrder" } ],
      "questions":[ Question ] } ]
}
Question = { "id","externalId":null|"A01","sectionId","dimensionId","text","type","helpText":null|"","mandatory":true,"sortOrder",
  "validation":null|{...},"config":null|{...},
  "options":[ { "id","label","score":null|3,"isNa":false,"sortOrder" } ] }
QuestionInput = { "dimensionId":null|int,"text","type","helpText","mandatory","validation","config",
  "options":[ { "id":null|int,"label","score":null|number,"isNa":false } ] }
```
- `POST /forms/{formId}/sections` `{ "name","code" }` -> 201 Section (empty `dimensions` and `questions`), appended last.
- `PUT /sections/{id}` `{ "name","code" }` -> 200 Section. `DELETE /sections/{id}` -> 204 (deletes its dimensions and questions).
- `POST /sections/{sectionId}/dimensions` `{ "name" }` -> 201 `{ "id","name","sortOrder" }`. `PUT /dimensions/{id}` `{ "name" }` -> 200. `DELETE /dimensions/{id}` -> 204 (deletes its questions).
- `POST /sections/{sectionId}/questions` `QuestionInput` -> 201 Question. If `dimensionId` is null use the section's first dimension; if the section has none, create one named `General`.
- `PUT /questions/{id}` `QuestionInput` (full replace) -> 200 Question. Options with an `id` are updated, options without are inserted, options missing from the list are deleted.
- `POST /questions/{id}/clone` -> 201 Question (placed right after the original, `externalId` null, options copied).
- `DELETE /questions/{id}` -> 204.
- **Order:** `PUT /forms/{formId}/order` `{ "type":"SECTIONS"|"DIMENSIONS"|"QUESTIONS", "parentId":null|int, "ids":[3,1,2] }` -> 204. `parentId` is null for SECTIONS, the section id for DIMENSIONS and QUESTIONS. `ids` must be exactly the current set under that parent, else 400. The server rewrites `sortOrder` as 1, 2, 3...
- Type rules enforced on save: `YES_NO` always has exactly two options `Yes`, `No` (server creates them); `LIKERT` always has five options `Strongly disagree, Disagree, Neutral, Agree, Strongly agree` (no scores); `SINGLE, MULTI, DROPDOWN, RANKING` need at least 2 options to publish; other types have no options.

## validation and config JSON
`validation` keys (all optional; ignore keys that do not apply to the type): `minLength` (int) and `maxLength` (int) for SHORT, LONG, EMAIL; `min`, `max` (numbers) and `integerOnly` (bool) for NUMBER; `minDate`, `maxDate` (`YYYY-MM-DD`) for DATE; `requireComment` (bool) for any type; `emailFormat` (bool, always true for EMAIL).
Builder Validation choices depend on question type: Minimum/Maximum length for SHORT, LONG and EMAIL; Number range for NUMBER; Email format for EMAIL; Date range for DATE; Evidence required (`requireComment`) for any type. Minimum/Maximum length expose editable integer bounds, Number range exposes editable numeric bounds, and Date range exposes Earliest date (`minDate`) and Latest date (`maxDate`). At least one bound is required for ranges; minimum/earliest must not exceed maximum/latest. The server rejects respondent answers outside configured bounds.
`config` keys: `allowDontKnow` (bool, NUMBER) and, for SCALE, `{ "min":1,"max":5,"minLabel":"","maxLabel":"" }`. RATING is fixed 1 to 5 (stars), NPS fixed 0 to 10.

## How each type is rendered, stored and checked
| Type | Respondent widget | Stored in `answers` | Server checks |
|---|---|---|---|
| SHORT | one-line input | value_text | min/max length |
| LONG | textarea 5 rows | value_text | min/max length |
| SINGLE | `.choice-list` one selectable | selected_option_id | option belongs to question |
| MULTI | `.choice-list` many | answer_options | all options belong to question |
| DROPDOWN | `.dd` | selected_option_id | option belongs to question |
| YES_NO | two choices Yes, No | selected_option_id | same |
| SCALE | `.scale-row` min..max | value_number | within min..max |
| RATING | `.scale-row` 1..5 with stars | value_number | 1..5 |
| NPS | `.scale-row` 0..10 | value_number | 0..10 |
| LIKERT | five choices | selected_option_id | same |
| RANKING | click options in order, rank number shown in the mark | answer_options with rank_no 1..n | all ranked options valid, no duplicates |
| DATE | date input | value_date | min/max date |
| TIME | time input | value_text `HH:mm` | format |
| NUMBER | number input (+ "Don't know" checkbox if `allowDontKnow`) | value_number, or value_text `DONT_KNOW` | min/max/integerOnly |
| EMAIL | email input | value_text | email format |
Scoring only uses `selected_option_id` options that have a `score`. `isNa` options are never scored. MULTI, RANKING, scale-number types, text types are not scored.
**Answered** means any of: optionIds not empty, text not empty, number not null, date not null. A comment alone does not count.

## Public and publishing
`GET /public/forms/{token}` (no auth) -> `{ "title","description","displayStatus":"OPEN"|"EXPIRED"|"CLOSED"|"NOT_OPEN","opensAt","expiresAt","requiresInvite":false }`. 404 `This link is not valid` for unknown or regenerated tokens.
`POST /forms/{id}/publish` `{ "opensAt":null|ts,"expiresAt":null|ts }` -> 200 `{ "link","shareToken","versionNo","opensAt","expiresAt","displayStatus" }`. Freezes the draft (previous published version becomes ARCHIVED), creates `shareToken` once (24 random bytes, base64url) and keeps it on later publishes. 422 `{ "status":422,"message":"Cannot publish","fieldErrors":[],"problems":[ { "questionId":12,"message":"At least 2 options required" } ] }` if there are no questions, a choice question has fewer than 2 options, or `expiresAt` is in the past.
`POST /forms/{id}/link/regenerate` -> 200 `{ "link","shareToken" }` (old token stops working, 404).
`POST /forms/{id}/unpublish` -> 204 (stored status `CLOSED`; the link then shows closed). Publishing again reopens it.
`GET /forms/{id}/versions` -> `[ { "versionNo","status","publishedAt","expiresAt","responseCount" } ]`.
Assignments (last phase): `GET /forms/{id}/assignments` -> `[ { "email","registered":true,"submissionStatus":null|"DRAFT"|"SUBMITTED" } ]`; `PUT /forms/{id}/assignments` `{ "emails":["a@x.com"] }` -> 200 `{ "count":1 }` (replaces the list; lowercased). When a form has assignments, only those emails can start it (403 `You are not invited to this form.`).

## Respondent
`POST /fill/{token}/start` -> 200 `{ "submissionId":5,"status":"DRAFT" }` (creates or returns the user's single submission for the current published version). Errors: 404 invalid token, 410 `This form closed on <date>.` for expired or closed, 403 `This form opens on <date>.`, 403 invited-only, 403 if the caller is an ADMIN (the landing page tells admins to sign out to answer).
`GET /submissions/{id}` -> 200
```
{ "id":5,"formId":1,"formTitle":"","status":"DRAFT","rowVersion":3,"versionNo":2,"expiresAt":null|ts,"readOnly":false,"allowEdit":true,
  "sections":[ { "id","code","name","questions":[ {
     "id","externalId","dimensionId","dimensionName","text","type","helpText","mandatory","validation","config",
     "options":[ { "id","label","isNa" } ],
     "answer": null | { "questionId","state":"ANSWERED","optionIds":[],"text":null,"number":null,"date":null,"remarks":null } } ] } ] }
```
Scores are never sent to respondents. `readOnly` is true when expired, closed, or submitted with `allowEdit` false. Questions come in section order, then `sortOrder`.
`PUT /submissions/{id}/answers` header `If-Match: "<rowVersion>"`, body `{ "answers":[ Answer, ... ] }` where `Answer = { "questionId","state","optionIds":[],"text":null,"number":null,"date":null,"remarks":null }` -> 200 `{ "rowVersion":4,"lastSavedAt":"ts","answeredCount":17,"totalCount":72 }`.
- Send only changed answers. An answer with no value and state `ANSWERED` deletes the row (unless `remarks` is set). An answer with no value and state `REVIEW` keeps a flag-only row.
- 409 when `If-Match` is stale (client reloads `GET /submissions/{id}`). 410 when expired or closed. 403 when the submission belongs to someone else (return 404 instead). 422 `fieldErrors` with `field` = `answers[n].questionId` for invalid values.
- On a SUBMITTED submission edits are allowed until expiry if `allowEdit`; clearing a mandatory answer is rejected with 422.
`POST /submissions/{id}/submit` -> 200 `{ "status":"SUBMITTED","submittedAt":"ts" }`. 422 `{ "status":422,"message":"Required answers missing","fieldErrors":[],"missing":[ { "questionId":7,"externalId":"B02","reason":"REQUIRED"|"INVALID","message":"" } ] }`.
**Client autosave:** debounce 800 ms, merge changed answers into one request, one request in flight at a time, update `rowVersion` from every response, on 409 show toast `Updated elsewhere. Reloaded.` and reload, on 410 switch to read-only. Edits autosave with state `ANSWERED`; "Mark for review" toggles `REVIEW`. Unanswered questions remain pending until answered. Dot color: `REVIEW` warn, answered accent, otherwise tint; the current question is marked with an accent ring.

## Responses (ADMIN). The Respondents tab includes submissions from all versions. By question and Scores default to the current published version and can select another version.
`GET /forms/{id}/responses` -> `{ "form":{ "id","title","versionNo" },"currentVersionId":3,"versions":[ { "id","versionNo","status","publishedAt" } ],"totalQuestions":72,"scoredQuestions":18,"respondents":[ { "submissionId","versionId","userId","name","email","versionNo","status","answeredCount","totalCount","progressPct","score":null|2.85,"submittedAt":null|ts,"lastSavedAt" } ] }`. `score` is the average of the respondent's scored answers (2 decimals). `totalCount` is calculated from that submission's version.
`GET /forms/{id}/submissions/{sid}` -> `{ "submission":{ "id","name","email","status","versionNo","submittedAt","lastSavedAt","answeredCount","totalCount","score" },"sections":[ { "id","code","name","questions":[ { "id","externalId","dimensionName","text","type","answerText":null|"","score":null|3,"remarks":null|"","state":null|"ANSWERED" } ] } ] }`.
`answerText` format: SINGLE, DROPDOWN, YES_NO, LIKERT = option label; MULTI = labels joined with ` | `; RANKING = `1. A | 2. B`; NUMBER = number or `Don't know`; others the raw value; no answer = `null`.
`GET /forms/{id}/questions/summary?versionId=&sectionId=` (`versionId` defaults to current published, `sectionId` optional) -> `[ { "questionId","externalId","sectionId","sectionCode","sectionName","dimensionName","text","type","answeredCount","distribution":null|[ { "label","count","pct" } ],"avgScore":null|2.9 } ]`. `distribution` is null for text, number, date, time types.
`GET /forms/{id}/questions/{qid}/answers?versionId=` -> `[ { "submissionId","name","email","status","answerText","score","remarks" } ]` (one entry per respondent; `answerText` null if unanswered).
`GET /forms/{id}/scores?versionId=` -> `{ "target":3.0,"overall":null|2.7,"sections":[ { "id","code","name","score":null|2.8,"dimensions":[ { "id","name","score":null|2.6,"scoredQuestions":3 } ] } ],"heat":[ { "questionId","externalId","sectionId","avgScore":null|2.5 } ] }`.
Score row dot position = `(score - 1) / 3 * 100` percent; target line at `(target - 1) / 3 * 100`. Color: score >= target accent, >= target - 1 warn, below bad. Unscored questions are tint in the heat strip.

## Exports (ADMIN). Frontend downloads with Axios `responseType: 'blob'` (the bearer token is required) and a temporary link.
`GET /forms/{id}/export?format=wide|long&versionId=` -> `text/csv; charset=UTF-8`, starts with a BOM, filename `<title-slug>-responses-<format>.csv`. Wide export defaults to the current published version and accepts a selected `versionId`; long export includes all versions.
- **wide** (current published version): `Respondent,Email,Status,Progress %,Score,Submitted at,<Q_ID for each question>`; one row per respondent; each cell is `answerText`.
- **long** (all versions): `Respondent,Email,Status,Q_ID,Section,Dimension,Question,Type,Answer,Score,Comment,Submitted at`; one row per answer.
`GET /forms/{id}/submissions/{sid}/export` -> per person, filename `<respondent-slug>-response.csv`, columns `Q_ID,Section,Dimension,Question,Type,Answer,Score,Comment`, one row per question (empty answer if unanswered).
All CSV cells are quoted, quotes doubled, and any cell starting with `=`, `+`, `-` or `@` gets a leading `'`. If a question has no `externalId`, use `Q` + id.
