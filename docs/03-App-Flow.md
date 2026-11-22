# 03 App Flow

## Pages
| Route | Who | Purpose |
|---|---|---|
| `/login` | all | Email + password login. Role is read from the account. |
| `/register` | respondent | Create a respondent account (name, email, password). |
| `/f/:token` | public | Landing page for a share link: title, description, status, **Start** button. |
| `/admin/forms` | admin | Forms table (home for admin). |
| `/admin/forms/:id/builder` | admin | Form builder. |
| `/admin/forms/:id/responses` | admin | Responses with tabs Respondents, By question, Scores. |
| `/admin/forms/:id/responses/:submissionId` | admin | One person's answers. |
| `/fill/:submissionId` | respondent | Answering screen. |
| `/fill/:submissionId/review` | respondent | Review and submit. |
| `/fill/:submissionId/done` | respondent | Submitted confirmation with Edit button. |

## Navigation
- Admin: top bar with brand, **Forms**, then **Builder** and **Responses** when inside a form, plus user name and Sign out on the right. Same visual style as `reference/assay-ui-reference.html`.
- Respondent: slim top bar with brand, "Draft saved" indicator, user name, Sign out.
- Public landing and auth pages: no top bar, split layout (see reference login).

## Auth flow
- Admin: `/login` -> `/admin/forms`.
- Respondent: `/register` or `/login` -> back to the link they came from (`returnTo`), else a simple "no forms assigned" page.
- Visiting any protected route while logged out -> `/login?returnTo=<route>`. Wrong role -> own home.
- Logout -> `/login`.

## Journey 1: admin creates and publishes
1. Forms table -> **New form** -> choose **Import CSV** or **Start blank** and enter a title.
2. Import shows errors with row and column, or creates the draft and opens the builder.
3. Edit sections, dimensions and questions. **Save draft** keeps it. **Publish** opens the drawer: set expiry date-time -> **Publish** -> link appears with **Copy**, **Regenerate**, **Open as respondent**.
4. Forms table now shows the form as Open.

## Journey 2: respondent answers
1. Opens `/f/:token` -> sees title and status -> **Start** -> logs in or registers -> returned to the link -> **Start** creates or resumes the submission.
2. `/fill/:id` shows the progress bar, the section index with dot matrix, and one question at a time. The **Show all questions in this section** toggle lists the whole section on one page.
3. Each change autosaves. **Mark for review** toggles the review dot; unanswered questions remain pending. **Next** is blocked on an empty mandatory question.
4. After the last question the review screen lists each section's answered count and any missing mandatory questions. **Submit** -> done page. **Edit my response** returns to `/fill/:id` until expiry.

## Journey 3: admin reads results
Forms table -> **Open** on a form with responses -> Responses. Click a person to see their answers and **Download CSV**. In **By question**, click a row to expand every respondent's answer. **Export all** downloads wide or long CSV.

## Empty states (one line of text, no illustration)
- No forms: "No forms yet." beside the New form button.
- Form without questions: "No questions. Add one or import a CSV."
- No responses: "No responses yet."
- Section without questions in respondent view is skipped.

## Error and loading states
- Loading: the word "Loading" in muted text, no spinners.
- Network or server error: toast at the bottom, message from the API.
- Expired link: landing page shows "This form closed on <date>". Revoked or unknown token: "This link is not valid."
- Save conflict (409): toast "Updated elsewhere. Reloaded." and reload the submission.
- Expired while answering: save is rejected with 410 and the screen becomes read-only with the message.

## Redirects
After login by role (above). After import -> builder. After submit -> done page. Session refresh failure -> `/login`.
