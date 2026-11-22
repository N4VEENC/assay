# Assay: standing rules for the coding agent (read this first on every task)

## Source of truth (in this order)
1. `docs/01-PRD.md` to `docs/08-Acceptance-Checklist.md`. **`docs/07-API-Contract.md` defines every endpoint and JSON field; if it disagrees with docs 02 or 05, doc 07 wins. Use its field names exactly.**
2. `design/design-system.css` (the only stylesheet for the whole app)
3. `db/schema.sql` (the database; do not change it unless asked)
4. `reference/assay-ui-reference.html` (visual and interaction reference only; its sample data is fake)
5. `sample/QE_Assessment_PreRead_Questionnaire_App_Leads.csv` (the exact import format)

## Stack lock (do not add anything else without asking)
- Backend: Java 17, Spring Boot 3.3.x, Maven, Spring Web, Spring Security with JWT (oauth2-resource-server + Nimbus), Spring Data JPA, JdbcTemplate (CSV import), MySQL 8. No Docker, no Spring Batch, no Flyway, no Lombok, no MapStruct, no Swagger.
- Frontend: React 18 + plain JavaScript (no TypeScript), Vite, React Router, Axios. No UI kit, no Tailwind, no state or chart or drag-and-drop libraries.
- Keep the database portable to PostgreSQL: no ENUM, no vendor functions, no stored procedures.

## Design lock (the UI must never drift)
- Import `design/design-system.css` once in `main.jsx`. Every screen is built only from its classes.
- No hard-coded colors, fonts, font sizes or spacing in JSX or other CSS files. Use the CSS variables.
- **No cards, no box shadows, no gradients, no rounded boxes.** Structure comes from hairline rules, whitespace and typography.
- Dropdowns always use the custom `.dd` component. Never a native `<select>`.
- Every list row that can be reordered has the `.handle` drag handle and the FLIP slide animation.
- If a new feature needs a component that does not exist, add it to `design-system.css` first, in the same style, and list it in `docs/04-UI-UX-Design-Brief.md`. Then use it.
- Tone of text: short and plain. No marketing sentences, no summary headlines like "X sits below target".

## Code rules
- Layers: Controller (DTOs, validation) -> Service (rules, ownership checks, transactions) -> Repository. Never return entities from controllers.
- Every endpoint checks the role and ownership (admin owns the form, respondent owns the submission).
- Parameterized queries only. Times are stored in UTC. CSV exports must prefix cells starting with = + - @ with a single quote.
- Passwords are hashed with BCrypt. Tokens follow `docs/05-Backend-Schema.md`.
- Write a test for every security rule and for the CSV import.

## Process
- Work on one phase or prompt at a time, in the order of `docs/06-Implementation-Plan.md`.
- After each task run the matching lines of `docs/08-Acceptance-Checklist.md` and report pass or fail for each.
- At the end of each task print: files changed, how to run, how to test, and what is not done.
- If a requirement is unclear, choose the simplest option that matches the reference HTML and note the assumption. Do not ask many questions.
