# 04 UI/UX Design Brief

**Visual source of truth:** `design/design-system.css` (tokens and components) and `reference/assay-ui-reference.html` (layout and behavior). In the reference file, every "dimension" tab means a **Section** in the real app; show the Dimension as a small label on each question. The reference's sample data is fake.

| Item | Decision |
|---|---|
| Aesthetic | Ledger / editorial. Information is organized by hairline rules, whitespace and type, like a printed report. Calm, precise, no decoration. |
| Mode | Light only |
| Background | `--c-bg` #E6E9E7 (cool mineral grey) |
| Text | `--c-ink` #12181C. Secondary `--c-muted` #66717A |
| Lines | `--c-line` #BEC6C4 (hairlines), `--c-tint` #D3DAD8 (empty bars, hover) |
| Accent | `--c-accent` #1F7A72 (verdigris): links, active states, progress, selected options |
| Status colors | `--c-bad` #A4403F (errors, low scores, delete), `--c-warn` #B58B2F (review, mid scores), `--c-info` #5B7C99 (draft) |
| Fonts | **Familjen Grotesk** for all text. **Fragment Mono** for IDs, numbers, dates, eyebrows and table headers. |
| Corners and depth | Square corners everywhere. Only dots, switches and the progress dots are round. No shadows, no gradients. |
| Brand | Wordmark `assay.` in Familjen Grotesk 700 with the dot in the accent color. |

## Hard rules (never break)
1. **No cards or boxed panels.** Use rules, columns and whitespace.
2. **No native `<select>`.** Use the `.dd` dropdown with an icon on every item.
3. Inputs are underline-only. Buttons are either `.btn-solid` (ink fill) or `.link-btn` (accent text). Only one `.btn-solid` per area.
4. Drag handles are the small `⠿` glyph (`.handle`). Reordering uses the FLIP slide animation (about 280 ms).
5. Inline editing: clicking a row expands an editor under it (`.q-editor`), never a side panel.
6. Use numbers, bars and dots instead of explanatory sentences. No summary headlines.
7. Colors and sizes come only from CSS variables. No inline colors.
8. Status is shown with a `.status-dot` plus the word. Never a colored pill or badge.

## Component inventory (all defined in `design-system.css`)
Top bar and tabs, eyebrow label, solid button (normal and XL), link button (normal and danger), icon button (+), underline field, dropdown, switch, ruled table (clickable rows), status dot, overlay scrollbar indicator, line bar, 10-block meter, stacked bar, builder side list item, keyboard and pointer accessible builder and respondent splitters, dimension group heading, question row with handle, inline question editor, aligned mandatory control, question state actions, save status, option row, section progress segments, section index with aligned counts and dot matrix, dot matrix legend, choice list (radio/checkbox rows), numeric scale row, number "Don't know" checkbox row, question block, highlighted paragraph answer field, compact optional comment field, score row (dot on a track with target line), heat strip, key-value strip, drawer with overlay, toast, auth split layout.

## Screen specifications
- **Forms table:** page contains only a right-aligned `.btn-solid--xl` "+ New form" and the table. No headings or sentences. Columns: Form (name + version in mono), Status, Assigned, Responded (n / N + line bar), Not responded (number with dot), Submitted (10-block meter + n / N), Not submitted, Completion (percent + stacked bar: submitted accent, in progress warn, not started tint), Created, Expires, Open link.
- **Builder:** Header row with title input, version, Save draft and Publish. Left column contains only the Sections list with its add control. A keyboard and pointer accessible splitter resizes the section list and question canvas. Main column has the section name, one Delete section action, then dimension groups with editable names, question counts and mapped question rows; dimensions can be added and removed here. The inline question editor keeps Save question as its primary action, with Clone, Delete and Close grouped separately. The Mandatory switch and its explanation share one aligned control.
- **Question types dropdown (icon + label):** Short answer ▭, Paragraph ☰, Multiple choice ◉, Checkboxes ☑, Dropdown ▾, Yes / No ⇄, Linear scale ┈, Rating ☆, Likert ▤, Net promoter ◔, Ranking ⇅, Date ▦, Time ◷, Number #, Email @.
- **Respondent:** Top progress line shows answered and remaining counts with clear segments per section. On desktop, the left section index and right question area scroll independently, separated by a resizable divider; section counts use a fixed aligned column. The right pane keeps section status and the Show all questions switch visible while questions and navigation scroll beneath. Changing section gives a brief transition. On narrow screens, sections and questions stack into normal page flow. Saved state is shown with a status dot and text in the top bar.
- **Responses:** tabs Respondents, By question, Scores. Right side: "Export all, wide" and "Export all, long" links. Respondents lists every response version and has a version filter with All versions; row click opens person detail (key-value strip, answers grouped by section, Download CSV solid button). By question and Scores have version dropdowns; By question also has a section dropdown showing section code and full name, rows with answer distribution stacked bar, and a nested answer table. Scores show score rows per dimension (dot on a 1 to 4 track with dashed target line), then the question heat strip.
- **Publish drawer:** right-side `.drawer` with expiry date-time, read-only link field with Copy, Regenerate, Open as respondent, Close.
- **Auth pages:** two-column `.auth-layout`, brand and a short line on the left, form on the right.

## Responsive and accessibility
- Under 1000 px: single column, index list moves above the question, builder and respondent panes stack and hide their splitters, tables scroll horizontally. On wider screens, respondent index and question panes scroll independently. Text and spacing scale fluidly with viewport width. Scrollbars remain hidden while idle and appear during scrolling. Hover transitions are subtle and respect reduced-motion settings.
- Contrast of text on background at least 4.5:1. Focus outline: 2 px accent underline or ring on inputs and buttons. All controls reachable by keyboard; dropdowns open with Enter or Space and close with Escape.
- Copy is short and neutral. Dates shown as `31 Dec 2026, 18:00` in the viewer's time zone.

## Change protocol (when adding or removing features)
1. Reuse existing components. 2. If something new is needed, extend `design-system.css` first, matching the rules above, and add it to the inventory here. 3. Never introduce new colors, fonts, shadows, radii or a UI library. 4. Compare the result with `reference/assay-ui-reference.html` before finishing.
