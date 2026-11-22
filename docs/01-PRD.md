# 01 PRD: Product Requirements

| Field | Value |
|---|---|
| App name | **Assay** |
| Tagline | Build an assessment from a spreadsheet, share one link, read the results. |
| Problem | Teams run maturity and readiness assessments in spreadsheets and generic form tools. Progress, scoring and per-person review are manual and slow. Admins cannot see who answered what, or which areas score low. |
| Target users | **Admin:** a quality or program lead who builds assessments, publishes them and analyzes results. **Respondent:** an application lead who receives a link and answers about 70 questions across sections, possibly over several sittings. |
| Core value | Import the questionnaire as CSV, publish a link, respondents answer one section at a time with clear progress, admin gets per-person answers, section and dimension scores and CSV exports. |

## Must have (v1)
1. Email + password login for admin and respondents (no email verification, no password reset).
2. Forms page: one table listing every form with status, assigned, responded, not responded, submitted, not submitted, completion, created and expiry date-time. A prominent **New form** button.
3. Create a form by importing the CSV in the exact sample format, or from scratch.
4. Form builder: add, edit, delete, clone and drag-reorder sections, dimensions and questions. 15 question types. Options with scores, mandatory switch, validation, draft save.
5. Publish: freezes a version, generates a share link, sets an expiry date-time, can regenerate or revoke the link.
6. Respondent flow: open link, log in or register, answer section by section, autosave, progress bar, dot matrix per section, mark for review, save as draft, review screen, submit, edit until expiry.
7. One response per respondent per form (duplicate prevention).
8. Responses page: respondents table, per-person answer view with CSV download, by-question view that expands to show every respondent's answer, scores view, and export of all responses (wide and long CSV).
9. Versioning: editing a published form creates a new draft version. Existing responses stay on their version.

## Should have (after v1 works)
- Assign respondents by pasting emails (restricts the form to them and enables the Assigned / Not responded counts).

## Out of scope for this version
Email sending or verification, password reset, file uploads, conditional logic, multiple organizations, reviewer role and manual scoring of text answers, anonymous responses, translations, charts library, SSO, mobile app.

## User stories
- As an admin, I want to import my 72-question CSV so that I do not retype it.
- As an admin, I want to reorder and edit questions so that I can refine the form before publishing.
- As an admin, I want a shareable link with an expiry so that I control who can answer and until when.
- As a respondent, I want to see how far along I am per section so that I know what remains.
- As a respondent, I want my answers saved automatically so that I can return later.
- As a respondent, I want to edit my submission before the deadline so that I can correct mistakes.
- As an admin, I want to open one respondent and see exactly what they answered, and download it.
- As an admin, I want to click a question and see every respondent's answer to it.

## Success metrics
- The sample CSV imports with 0 errors into 11 sections, 23 dimensions and 72 questions. The CSV contains separate `Evidence` and `Other` dimensions in section K.
- A respondent can finish all 72 questions across two sessions without losing an answer.
- A second submission by the same respondent is impossible.
- Per-person and all-response CSVs open correctly in Excel.
