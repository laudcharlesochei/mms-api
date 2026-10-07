# Mentor Management System — API (V1: Spring Boot · Heroku · JawsDB)

The REST API for the University of Dundee Graduate Apprenticeship **Mentor Management System (MMS)**.
It gives the GA programme one central record of every mentor meeting — that it happened, when, and what
was discussed — and shows the Programme Director at a glance which students are behind.

This is **Version 1**: Java 21 + Spring Boot 3.5 on Heroku (EU), data in **JawsDB MySQL 8**.
The companion frontend is the `mms-web` (V1) repository. Version 2 keeps the same API but stores
data in SharePoint lists.

> **Data protection:** V1 must not hold real student data until UoD Information Governance and IT have
> approved external hosting in writing (Section 9 of the technical documentation). Use synthetic data
> (`SEED_DEMO_DATA=true`) for development, demos and the pilot.

---

## What it does (requirements → code)

| Area | Requirements | Where |
|---|---|---|
| Sign-in: local email + password + TOTP MFA, or Entra ID SSO; lockout after 5 failures; 30-min idle timeout | FR-01, FR-04 | `auth/`, `service/AuthService`, `config/SecurityConfig` |
| Role + data-scope checks on every request (ADMIN, DIRECTOR, PROG_LEAD, AOS, STUDENT) | FR-02 | `service/ScopeService`, `@PreAuthorize` |
| Users, roles, deactivation, invitations | FR-03 | `service/UserAdminService`, `/users` |
| Academic years, required meetings, editable periods | FR-05 | `service/AcademicYearService`, `/academic-years` |
| Programmes, students, employers, workplace mentors | FR-06 | `service/ReferenceDataService` |
| CSV/XLSX allocation import with preview, row errors, idempotent commit | FR-07, FR-08 | `service/ImportService`, `/imports` |
| Mid-year AoS reassignment with history | FR-09 | `service/AllocationService` |
| Meeting form: draft/submit, date rules, duplicate warning, previous meeting, amend window, revisions, 2,000-char notes | FR-10 – FR-16 | `service/MeetingService`, `/meetings` |
| Student timeline, PDF laid out like the form, submit email (AoS, optional student) | FR-17 – FR-20 | `/students/{id}/meetings`, `service/PdfService`, `service/MailService` |
| Dashboard tiles, per-student / per-AoS tables, filters, drill-down, CSV export | FR-21 – FR-24 | `service/DashboardService`, `/dashboard` |
| Audit log; soft delete with reason, restore by Admin | FR-25, FR-26 | `service/AuditService` + `@Auditable` aspect |
| Notes encrypted at rest (AES-256-GCM), HTML stripped, security headers, CORS, rate limiting | NFR-07, 9.3 | `util/NotesCipher`, `config/*` |
| Storage behind interfaces (swap to SharePoint in V2) | NFR-14 | `storage/` (interfaces) and `storage/jpa/` |
| Retention purge, subject-access export and erasure | NFR-10, 9.2 | `service/RetentionJob`, `service/SubjectAccessService` |

### Carried over from the earlier MentorSync (Django) app

The Django app's programmes / enrollments / progress logs are replaced by the documented model
(programmes, students, advisor allocations, mentor meetings). These MentorSync features were kept
because they support the MMS goals:

| MentorSync feature | MMS equivalent |
|---|---|
| `invites` app (email invite token → set password) | Local-account invitations: `POST /users/{id}/invite`, `/auth/invites/*` |
| `messaging` app (contacts, conversations, read receipts) | `/messages` — staff ↔ staff and AoS ↔ advisee; `FEATURE_MESSAGING` toggle |
| `send_progress_reminders` command | `ReminderJob` — weekly email to each AoS listing advisees Due/Overdue, once per period (`REMINDERS_ENABLED`) |
| `ProgressLog` review / `Feedback` | Folded into the structured meeting record (agreed actions, engagement concern) |
| `seed_db` command | `DemoDataSeeder` (synthetic only) |
| drf-spectacular Swagger | springdoc-openapi (`/swagger-ui.html`) |

---

## Run it locally (no MySQL needed)

Requirements: **Java 21** and **Maven 3.9+**.

```bash
mvn spring-boot:run
```

The default `local` profile uses a file-based H2 database in MySQL mode (`./data/`), runs the Flyway
migrations, creates an Administrator and loads synthetic demo data.

* API: http://localhost:8080/api/v1 · Swagger UI: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/actuator/health
* Start the frontend (`mms-web`) on http://localhost:5173.

| Demo account | Roles | Password |
|---|---|---|
| `admin@mms.local` | ADMIN (bootstrap) | `ChangeMe123!` |
| `admin.ops@mms.local` | ADMIN | `Password123!` |
| `director@mms.local` | DIRECTOR, AOS | `Password123!` |
| `lead.se@mms.local` / `lead.ds@mms.local` | PROG_LEAD, AOS | `Password123!` |
| `aos1@mms.local` … `aos3@mms.local` | AOS | `Password123!` |
| `student@mms.local` | STUDENT (read-only, Phase 2) | `Password123!` |

To start again from scratch, stop the app and delete the `data/` folder.

### Against MySQL 8 (same engine as JawsDB)

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### Tests

```bash
mvn verify          # unit tests + API integration tests (H2) + JaCoCo report in target/site/jacoco
```

---

## Deploy to Heroku (EU) with JawsDB

> **Full step-by-step procedure, verification checks and troubleshooting: [DEPLOY-HEROKU.md](DEPLOY-HEROKU.md)** (the same guide is in both V1 repositories).

```bash
heroku create uod-ga-mms-api --region eu
heroku addons:create jawsdb:kitefin -a uod-ga-mms-api        # leopard for production
heroku buildpacks:set heroku/java -a uod-ga-mms-api
heroku config:set -a uod-ga-mms-api \
  SPRING_PROFILES_ACTIVE=prod \
  AUTH_MODE=local \
  JWT_SECRET="$(openssl rand -base64 48)" \
  NOTES_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  CORS_ALLOWED_ORIGIN=https://uod-ga-mms-web.herokuapp.com \
  FRONTEND_URL=https://uod-ga-mms-web.herokuapp.com \
  ADMIN_EMAIL=you@dundee.ac.uk ADMIN_PASSWORD='a-strong-password-1'
git push heroku main
```

* `JAWSDB_URL` is set by the add-on and turned into the datasource (TLS required) by
  `JawsDbEnvironmentPostProcessor`. The Hikari pool is 5 per dyno to stay under the JawsDB connection cap.
* The `release` phase in the `Procfile` runs the Flyway migrations before each release goes live.
* Store `NOTES_ENCRYPTION_KEY` in a password manager — losing it makes stored notes unreadable.
* For a staging/review app, `app.json` defines the add-on and config vars (set `SEED_DEMO_DATA=true`).
* Heroku is in sustaining-engineering mode; the `Dockerfile` keeps the API portable (Azure App Service, university hosting).

### Configuration (environment variables)

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local`, `mysql`, `staging`, `prod` | `local` |
| `JAWSDB_URL` | Set by the add-on | — |
| `AUTH_MODE` | `local` (email + password + TOTP) or `entra` | `local` |
| `JWT_SECRET` | Signing key for local-mode tokens | random (sessions lost on restart) |
| `ACCESS_TOKEN_MINUTES` / `IDLE_TIMEOUT_MINUTES` | Token lifetimes (refresh token = idle timeout) | 15 / 30 |
| `REQUIRE_MFA` | Force every local account to enrol TOTP | `false` |
| `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID`, `ENTRA_AUDIENCE`, `ENTRA_API_SCOPE` | Entra ID token validation / SPA config | — |
| `ENTRA_AUTO_PROVISION` | Create users on first SSO sign-in if the token carries MMS app roles | `false` |
| `CORS_ALLOWED_ORIGIN` | The mms-web origin (comma-separate several) | `http://localhost:5173` |
| `FRONTEND_URL` | Used in email links | `CORS_ALLOWED_ORIGIN` |
| `NOTES_ENCRYPTION_KEY` | Base64 32-byte AES key for notes columns | dev key (warns) |
| `EDIT_WINDOW_DAYS` | Days an AoS may amend a submitted record | 14 |
| `MAIL_ENABLED`, `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Submit email, invites, reminders | disabled |
| `MAIL_SEND_TO_AOS` / `MAIL_SEND_TO_STUDENT` / `MAIL_ATTACH_PDF` | FR-19 / FR-20 | true / false / true |
| `REMINDERS_ENABLED`, `REMINDERS_CRON` | Weekly AoS reminders | off, Mondays 09:00 UK |
| `RETENTION_ENABLED`, `RETENTION_YEARS` | Purge job | off, 6 |
| `FEATURE_MESSAGING` | Direct messages module | true |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | First Administrator (only if no users exist) | — |
| `SEED_DEMO_DATA` | Synthetic demo data | true locally, false in prod |
| `SWAGGER_ENABLED` | Swagger UI | true (false in prod) |

### Entra ID (SSO) mode

1. UoD IT registers an app `mms-api` (Expose an API → scope `access_as_user`; App roles `ADMIN`,
   `DIRECTOR`, `PROG_LEAD`, `AOS`, `STUDENT`) and an SPA app `mms-web` with redirect URI = the web URL.
2. Set `AUTH_MODE=entra`, `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID` (the API app), `ENTRA_AUDIENCE=api://mms-api`,
   `ENTRA_API_SCOPE=api://mms-api/access_as_user`.
3. Users are matched by Entra object id, or by email on first sign-in. Roles = database roles ∪ token app roles.

---

## API overview (`/api/v1`)

Full contract: `/v3/api-docs` (OpenAPI 3) and Swagger UI. Errors are RFC 9457 problem details with a
`code` and, for validation, an `errors` map. Meeting updates use `If-Match: "<version>"` (412 on conflict).

```
GET  /config                       public: auth mode, Entra ids, feature flags
POST /auth/login | /auth/login/mfa | /auth/refresh | /auth/logout | /auth/session
POST /auth/mfa/setup | /auth/mfa/enable | /auth/mfa/disable | /auth/password
POST /auth/invites/validate | /auth/invites/accept
GET  /me
GET  /advisees?year=               AoS home (S2): advisees with progress + my drafts
GET  /students?programme=&aos=&status=&q=&page=&size=
GET  /students/{id}                details + allocation history + current progress
POST /students  PUT /students/{id} DELETE /students/{id} (erasure, Admin)
GET  /students/{id}/meetings       GET /students/{id}/meetings/latest
POST /students/{id}/allocations    reassign AoS (FR-09)
GET  /students/{id}/subject-access subject access export (Admin)
POST /meetings                     create draft or submit (status in body)
GET  /meetings/{id}  PUT /meetings/{id}  POST /meetings/{id}/submit  DELETE /meetings/{id}
POST /meetings/{id}/restore        GET /meetings/{id}/pdf  GET /meetings/{id}/revisions
GET  /meetings/drafts              GET /meetings/deleted
GET  /dashboard/summary | /dashboard/students | /dashboard/advisors | /dashboard/students/export
CRUD /programmes /employers /mentors /academic-years (+ PUT /academic-years/{id}/periods)
POST /imports/allocations (multipart) → GET /imports/{id} → POST /imports/{id}/commit | /cancel
GET  /imports/template  GET /imports/{id}/errors.csv
CRUD /users  PUT /users/{id}/roles  POST /users/{id}/reset-mfa  POST /users/{id}/invite  GET /users/staff
GET  /audit?from=&to=&user=&entity=&action=   GET /audit/export
GET  /messages/contacts  GET /messages?with=  POST /messages  POST /messages/{id}/read
GET  /actuator/health
```

## Project structure

```
uk.ac.dundee.ga.mms
 ├─ config    security, CORS, JawsDB URL, rate limiting, correlation IDs, OpenAPI
 ├─ auth      current user, JWT → roles, Entra role mapping, local token service
 ├─ web       controllers, DTOs, RFC 9457 error handler
 ├─ domain    entities and enums
 ├─ service   MeetingService, DashboardService, ImportService, PdfService, MailService, AuditService, …
 ├─ storage   repository interfaces (NFR-14) + jpa/ implementations
 └─ util      AES-GCM notes cipher, TOTP, CSV, sanitiser
src/main/resources/db/migration   Flyway: V1__baseline.sql, V2__views.sql, V3__seed_reference.sql
src/main/resources/templates      PDF and email templates (Thymeleaf)
```
