# Assay local run guide

## Before you start

Install JDK 17, Node.js 18+, and MySQL 8. Maven does not need to be installed globally: the backend includes a wrapper that downloads Apache Maven 3.9.9 on first use. Run the commands below in PowerShell from the project folder unless a step says otherwise. On macOS/Linux, use `./mvnw` in place of `mvnw.cmd`.

MySQL defaults are already set in `backend/src/main/resources/application.yml`:

- User: `root`
- Password: `password@1234`
- Database: `assay`

You do not need to edit the datasource settings if your local MySQL root account uses those credentials. If the local password differs, set `DB_PASSWORD` in the backend terminal before starting Spring Boot. `DB_USER` can override `root` if needed. The seeded Assay admin login is separate from MySQL: `admin@assay.local` / `Admin@12345` by default. Change those with `ADMIN_EMAIL` and `ADMIN_PASSWORD` if desired.

## 1. Create the database

Open MySQL Workbench or a terminal and log in as root. For the terminal, enter the configured password when prompted:

```powershell
mysql -u root -p
```

At the MySQL prompt, create the database and exit:

```sql
CREATE DATABASE assay CHARACTER SET utf8mb4;
exit
```

From the project folder, load the schema:

```powershell
cmd /c "mysql -u root -p assay < db\schema.sql"
```

Enter `password@1234` when prompted. If you created the database in Workbench, open `db/schema.sql`, select `assay`, and run the file once.

## 2. Start the backend

Open a terminal in the project folder:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The first Maven run downloads dependencies. When Spring reports `Started AssayApplication`, check `http://localhost:8080/api/health`; it should return `{"status":"ok"}`.

If the database password is different, set it before starting Maven:

```powershell
$env:DB_USER = 'root'
$env:DB_PASSWORD = 'your-local-password'
.\mvnw.cmd spring-boot:run
```

Keep this terminal open.

## 3. Start the frontend

Open a second terminal in the project folder:

```powershell
cd frontend
npm install
npm run dev
```

Open the local address printed by Vite, usually `http://localhost:5173`.

## 4. Try the app

1. Sign in as `admin@assay.local` with `Admin@12345`.
2. Choose **+ New form**. Import `sample/QE_Assessment_PreRead_Questionnaire_App_Leads.csv` or create a blank form.
3. In the builder, edit sections, dimensions, and questions. Save the draft.
4. Publish and copy the form link.
5. Open that link in a private browser window. Register a respondent account, answer questions, and submit.
6. Return to the admin window and open Responses.

## 5. Useful checks

From the project folder:

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm run build
```

## Current implementation status

The app includes authentication, CSV import, forms overview, builder editing and reordering, publishing and version cloning, respondent autosave and submit, invitations, responses tabs, scores, and CSV export endpoints. The end-to-end flows still need broader verification against `docs/08-Acceptance-Checklist.md`; the sample import totals, all 15 answer types, refresh rotation, and every ownership/error case have not all been exercised. Use that checklist to track the remaining verification and fixes.
