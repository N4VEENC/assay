# 08 Acceptance Checklist (run after each prompt; report pass or fail for every line)

## Phase 1 to 3: setup and auth
1. `GET /api/health` works through the Vite proxy.
2. Admin from env logs in and lands on `/admin/forms`. Wrong password shows the same message as an unknown email.
3. A respondent registers, is logged in, and cannot open `/admin/forms` (redirected) or call `/api/forms/overview` (403).
4. Reload the browser: still logged in. Wait for the access token to expire (set 15 s in a test): the next request refreshes silently. Logout, then refresh is rejected.
5. Using an old refresh cookie after rotation returns 401.

## Phase 4 to 5: forms table and import
6. The forms table has every column in docs/04 and the XL "+ New form" button; no extra text on the page.
7. Importing `sample/QE_Assessment_PreRead_Questionnaire_App_Leads.csv` gives 11 sections, 23 dimensions, 72 questions, 53 mandatory, 18 questions with options scored 1 to 4, and "Don't know" flagged `isNa`.
8. A file with a duplicate Q_ID, a missing column, an unknown type and an empty question returns all four errors with rows and columns and saves nothing.
9. A file with semicolons, a BOM and quoted commas imports correctly.

## Phase 6: builder
10. Add, rename, delete, drag-reorder a section, a dimension and a question; reload keeps the order.
11. Changing a type to YES_NO creates Yes and No; to MULTI keeps options; to SHORT removes options. Every dropdown is the custom `.dd` with icons.
12. Mandatory switch, clone (placed right after the original), delete and validation values persist.
13. Dragging animates (rows slide, no jump) and only the handle starts a drag.

## Phase 7: publish
14. Publish with zero questions or a choice question with one option returns the 422 problems and the drawer shows them.
15. Publishing returns a link; opening it logged out shows the landing page with the form title.
16. Regenerate makes the old link say "This link is not valid". Expiry in the past is rejected; after the real expiry the landing page says the form closed.
17. Editing a published form creates version + 1 as a draft; existing submissions stay on the old version.

## Phase 8: respondent
18. Start twice returns the same submission. Two different respondents get separate submissions.
19. Answer five questions, reload: answers and dot states are kept. The progress numbers (`8/12 · 4 left`, overall remaining) are correct.
20. Mark for review toggles the warn dot; edits autosave automatically. The legend counts match answered, review and unanswered dots.
21. "Show all questions in this section" lists the whole section; turning it off returns to one question.
22. Submit with a missing mandatory answer is blocked and the review screen lists it. A valid submit shows the done page. Editing after submit works until expiry and is blocked after.
23. All 15 types render and validate per docs/07. Two tabs saving the same submission produce a 409 toast, not data loss.

## Phase 9: responses
24. With two submitted respondents, the Respondents table, per-person view, By question expansion and Scores all match the database.
25. The dot on a score row sits at `(score - 1) / 3`; target line at `(target - 1) / 3`; colors follow the rule in docs/07.
26. Wide, long and per-person CSVs open in Excel with correct accents, quoting and a leading `'` on cells beginning with `=`.

## Phase 10 and final
27. Empty, loading and error states from docs/03 exist.
28. No file outside `frontend/src/styles/design-system.css` defines colors, fonts or shadows; no native `<select>`; no card-like boxed panels.
29. No dependency outside the stack lock in `AGENTS.md`. `./mvnw test` and `npm run build` pass.
