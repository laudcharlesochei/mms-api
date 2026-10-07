# Deploying MMS V1 to Heroku with JawsDB — step by step

**Applies to:** `mms-api-v1` (Spring Boot backend) and `mms-web-v1` (React frontend) —
*Mentor Management System, Technical Documentation V1: Spring Boot · Heroku · JawsDB*.

The same file is in both repositories. Follow Parts A–F in order: the backend must be running
before the frontend can sign anyone in.

---

## How the pieces fit

```
Browser ──► mms-web (Heroku, Node/Express)      serves the React app + /config.js
   │            config var API_BASE_URL  ─────────────┐
   │                                                  ▼
   └──────────── HTTPS + bearer token ──────► mms-api (Heroku, Java 21 / Spring Boot)
                                                 config var CORS_ALLOWED_ORIGIN = mms-web URL
                                                 config var JAWSDB_URL (set by the add-on)
                                                          │
                                                          ▼
                                                 JawsDB MySQL 8 (Flyway creates every table)
```

Three facts that save most debugging time:

1. **You do not configure the database by hand.** The API reads `JAWSDB_URL` itself
   (`JawsDbEnvironmentPostProcessor`), converts it to a JDBC URL with TLS required, and Flyway
   creates all tables on the first deploy.
2. **You do not insert users with SQL.** On start-up the API creates the Administrator named in
   `ADMIN_EMAIL` with the password in `ADMIN_PASSWORD` (BCrypt-hashed).
3. **The frontend learns the API address at runtime** from the `API_BASE_URL` config var on the
   web app (served as `/config.js`). The API must allow the web app's exact address in
   `CORS_ALLOWED_ORIGIN`. If either is wrong, the sign-in page says *"Cannot reach the MMS server"*.

---

## Part A — One-time preparation (your computer)

| Tool | Version | Check |
|---|---|---|
| Git | any | `git --version` |
| Heroku CLI | latest | `heroku --version`, then `heroku login` |
| Java JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 20 or 22 | `node -v` |
| OpenSSL | any (for secrets) | `openssl version` (on Windows use Git Bash) |

Your Heroku account needs a verified payment method to add add-ons, even the free JawsDB plan.

Pick two app names. Heroku names are global, so add a prefix, e.g. `uod-ga-mms-api` and
`uod-ga-mms-web`. In the commands below replace them with yours.

---

## Part B — Prove it works locally first (10 minutes)

**Backend** (no MySQL needed — uses a local H2 database and demo data):

```bash
cd mms-api-v1
mvn verify                 # compiles and runs the unit + API tests; must end in BUILD SUCCESS
mvn spring-boot:run        # API on http://localhost:8080
```

Check in a browser: http://localhost:8080/actuator/health shows `{"status":"UP"}` and
http://localhost:8080/api/v1/config shows JSON.

**Frontend** (second terminal):

```bash
cd mms-web-v1
npm install
npm run dev                # http://localhost:5173
```

Sign in as `director@mms.local` / `Password123!` (or `admin@mms.local` / `ChangeMe123!`).

If this does not work locally, it will not work on Heroku — fix it here first.

---

## Part C — Put each project in its own GitHub repository

Each zip is one repository. Heroku builds from the repository root, so `pom.xml`
(backend) and `package.json` (frontend) must be at the top level, not inside a sub-folder.

```bash
cd mms-api-v1
git init && git add . && git commit -m "MMS API V1"
git branch -M main
git remote add origin https://github.com/<you>/mms-api-v1.git
git push -u origin main

cd ../mms-web-v1
git init && git add . && git commit -m "MMS Web V1"
git branch -M main
git remote add origin https://github.com/<you>/mms-web-v1.git
git push -u origin main
```

The `.gitignore` files already exclude `target/`, `node_modules/`, `dist/`, `data/` and `.env`.
Never commit real secrets.

---

## Part D — Backend: `mms-api-v1` on Heroku with JawsDB

Run these from inside the `mms-api-v1` folder.

### D1. Create the app (EU region)

```bash
heroku create uod-ga-mms-api --region eu
heroku buildpacks:set heroku/java -a uod-ga-mms-api
```

### D2. Add JawsDB

```bash
heroku addons:create jawsdb:kitefin -a uod-ga-mms-api     # free, 5 MB, 10 connections - demo/pilot only
# production: heroku addons:create jawsdb:leopard -a uod-ga-mms-api
```

Confirm the add-on set the variable (just look — **do not copy it anywhere or convert it**):

```bash
heroku config:get JAWSDB_URL -a uod-ga-mms-api
# mysql://<user>:<password>@<host>:3306/<generated_database_name>
```

### D3. Generate secrets

```bash
openssl rand -base64 48     # → JWT_SECRET (any long random string)
openssl rand -base64 32     # → NOTES_ENCRYPTION_KEY (must decode to exactly 32 bytes; this is 44 characters)
```

Save `NOTES_ENCRYPTION_KEY` in a password manager. If it is lost or changed, existing meeting
notes cannot be decrypted.

### D4. Set the config vars

Choose **one** profile:

* `staging` — for the demo/pilot with synthetic data; `SEED_DEMO_DATA=true` loads 40 demo students.
* `prod` — real use; demo data is always off.

```bash
heroku config:set -a uod-ga-mms-api \
  SPRING_PROFILES_ACTIVE=staging \
  AUTH_MODE=local \
  JWT_SECRET='<value from D3>' \
  NOTES_ENCRYPTION_KEY='<value from D3>' \
  ADMIN_EMAIL='your.name@dundee.ac.uk' \
  ADMIN_PASSWORD='ChooseAStrongPassword1' \
  ADMIN_NAME='Your Name' \
  SEED_DEMO_DATA=true \
  DB_POOL_SIZE=4 \
  CORS_ALLOWED_ORIGIN=https://placeholder.invalid
```

Notes:

* **Never** set `SPRING_PROFILES_ACTIVE=migrate`. `migrate` is added automatically, only in the
  release phase, by the `Procfile` (`--spring.profiles.include=migrate`). It makes the app apply
  migrations and exit — if the web process gets it, the API stops immediately after starting.
* `ADMIN_PASSWORD`: at least 10 characters with letters and numbers.
* `CORS_ALLOWED_ORIGIN` is a placeholder until the web app exists (Part E).
* `DB_POOL_SIZE=4` keeps the web dyno (4) plus the release phase under Kitefin's 10-connection cap.

### D5. Deploy

```bash
heroku git:remote -a uod-ga-mms-api        # adds the "heroku" git remote
git push heroku main
```

(Or: Heroku Dashboard → app → **Deploy** → GitHub → connect `mms-api-v1` → **Deploy Branch**.)

What you should see in the build output, in this order:

1. Maven build … `BUILD SUCCESS`
2. **Release phase**: Flyway lines such as `Migrating schema ... to version "1 - baseline"`, then
   `Database migrations applied - exiting (release phase).` **This exit is correct** — it is the
   one-off release command, not the web server.
3. `Released v…` and `Deployed to Heroku`.

### D6. Verify the API is running

```bash
heroku ps -a uod-ga-mms-api                # expect: web.1: up
heroku logs --tail -a uod-ga-mms-api
```

In the logs look for:

```
HikariPool-1 - Start completed.
Created bootstrap Administrator your.name@dundee.ac.uk
Started MmsApplication in … seconds
MMS API ready | profiles=[staging] | auth=local | database=jdbc:mysql://<host>:3306/<db> | CORS origin=… | users=… | encryption key set
```

Find the API's real address (new Heroku apps get a random suffix, e.g.
`uod-ga-mms-api-1a2b3c4d5e6f.herokuapp.com`):

```bash
heroku apps:info -a uod-ga-mms-api | grep "Web URL"
```

Then test it:

```bash
curl https://<api-web-url>/actuator/health        # {"status":"UP"}
curl https://<api-web-url>/api/v1/config          # {"authMode":"local", ... "variant":"V1" ...}
curl -X POST https://<api-web-url>/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"your.name@dundee.ac.uk","password":"ChooseAStrongPassword1"}'
# → JSON with "accessToken": the database, users and password hashing all work
```

If any of these fail, go to **Part F** before touching the frontend.

---

## Part E — Frontend: `mms-web-v1` on Heroku

Run these from inside the `mms-web-v1` folder.

### E1. Create the app

```bash
heroku create uod-ga-mms-web --region eu
heroku buildpacks:set heroku/nodejs -a uod-ga-mms-web
```

### E2. Point it at the API

Use the API **Web URL** from D6 — scheme and host only: no trailing `/`, no `/api/v1`.

```bash
heroku config:set -a uod-ga-mms-web \
  NODE_ENV=production \
  API_BASE_URL=https://uod-ga-mms-api-1a2b3c4d5e6f.herokuapp.com
```

The config var is `API_BASE_URL`, read at runtime by `server.js`. (`VITE_API_BASE_URL` is only a
build-time fallback; you do not need it on Heroku.)

### E3. Deploy

```bash
heroku git:remote -a uod-ga-mms-web
git push heroku main
```

Heroku installs packages, runs `npm run build`, then starts `node server.js` (`Procfile`).

### E4. Tell the API to accept this site (CORS)

Get the web app's exact address and put it on the **API** app:

```bash
heroku apps:info -a uod-ga-mms-web | grep "Web URL"
heroku config:set -a uod-ga-mms-api \
  CORS_ALLOWED_ORIGIN=https://uod-ga-mms-web-9f8e7d6c5b4a.herokuapp.com \
  FRONTEND_URL=https://uod-ga-mms-web-9f8e7d6c5b4a.herokuapp.com
```

It must match the address in the browser bar exactly (`https://`, same host). Setting a config var
restarts the API automatically.

### E5. Verify end to end

1. Open `https://<web-url>/config.js` — it must show your API address:
   `window.__MMS_CONFIG__ = {"apiBaseUrl":"https://uod-ga-mms-api-….herokuapp.com", …}`
2. Open `https://<web-url>/` and sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
3. As Administrator: **Users & roles** → add real staff (each gets an invitation link; with email off
   the link is shown on screen to send yourself). With `SEED_DEMO_DATA=true` you can also sign in
   as `director@mms.local` or `aos1@mms.local` with `Password123!`.
4. **My account** → turn on two-step verification.

Done. Once you have signed in successfully, change the `ADMIN_PASSWORD` config var to something
nobody else knows or remove it (it is only used when the account does not exist yet, or with
`ADMIN_RESET`).

---

## Part F — Troubleshooting

### "Cannot reach the MMS server at …" on the sign-in page

The message shows the address the browser tried. Work down this list:

| Check | How | Fix |
|---|---|---|
| Address is `http://localhost:8080` | The web app has no `API_BASE_URL` | `heroku config:set API_BASE_URL=https://<api-web-url> -a uod-ga-mms-web` |
| Address is wrong or ends in `/api/v1` | Compare with `heroku apps:info -a uod-ga-mms-api` | Set it to scheme + host only |
| API not running | `heroku ps -a uod-ga-mms-api` shows `crashed` / nothing | See "API crashes on start" below; `heroku ps:scale web=1` if no web dyno |
| CORS blocked | Browser DevTools (F12) → Console shows *"blocked by CORS policy"* | Set `CORS_ALLOWED_ORIGIN` on the **API** to the exact web URL (E4) |
| API asleep (Eco dynos) | First request after 30 min idle takes ~30 s | Reload the page, or use Basic dynos |

### API crashes on start (logs show `Application run failed` / `State changed from starting to crashed`)

| Log message contains | Cause | Fix |
|---|---|---|
| `NOTES_ENCRYPTION_KEY must decode to 32 bytes` | Key is not 32 bytes | `heroku config:set NOTES_ENCRYPTION_KEY="$(openssl rand -base64 32)"` (only before real notes exist) |
| `Communications link failure` / `Access denied for user` | Database unreachable or credentials changed | `heroku addons -a uod-ga-mms-api` (is JawsDB attached?), then `heroku restart` |
| `User ... has exceeded the 'max_user_connections'` | Kitefin's 10-connection cap | `heroku config:set DB_POOL_SIZE=3`; close any DB client (Workbench/DBeaver) connections |
| `SSL connection error` / `Communications link failure` right after adding the add-on | TLS handshake to the database | Usually transient: `heroku restart`. To diagnose only: `heroku config:set JAWSDB_SSL_MODE=PREFERRED`, then set it back |
| `Error R14 (Memory quota exceeded)` | Dyno memory | `heroku config:set JAVA_OPTS="-XX:MaxRAMPercentage=70 -Xss512k"` or a larger dyno |
| `Error R10 (Boot timeout)` | Slow start on a small dyno | Retry `heroku restart`; use a Basic dyno rather than Eco |
| `Validate failed: Migrations have failed validation` | A released Flyway script was edited | Never edit `V1__…sql`–`V3__…sql`; add a new `V4__…sql` instead |
| The app starts, prints `exiting (release phase)` and stops | `migrate` is in `SPRING_PROFILES_ACTIVE` | `heroku config:set SPRING_PROFILES_ACTIVE=staging` (or `prod`) |

### Sign-in says "Email or password is incorrect"

* Logs say `users=0` or `No users in the database` → `ADMIN_EMAIL` / `ADMIN_PASSWORD` were not
  set: set them, then `heroku restart -a uod-ga-mms-api`. The Administrator is created on start-up.
* The Administrator exists but the password is unknown, the account is locked (5 failed attempts →
  15 minutes) or the authenticator phone is lost:
  ```bash
  heroku config:set ADMIN_PASSWORD='NewStrongPassword2' ADMIN_RESET=true -a uod-ga-mms-api
  # wait for "ADMIN_RESET=true: reset password, MFA and lockout ..." in the logs, then:
  heroku config:unset ADMIN_RESET -a uod-ga-mms-api
  ```

### "INSERT command denied" or saves start failing (Kitefin 5 MB limit)

JawsDB removes INSERT rights when the free 5 MB is used up. Check the size:

```sql
SELECT ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS size_mb
FROM information_schema.tables WHERE table_schema = DATABASE();
```

The demo data uses well under 1 MB, but the audit log grows with every action. Upgrade with
`heroku addons:upgrade jawsdb:leopard -a uod-ga-mms-api` (1 GB) before a pilot.

### Looking inside the database (optional, read-only checks)

Use the values from `heroku config:get JAWSDB_URL` (user, password, host, database) in MySQL
Workbench, DBeaver or HeidiSQL, or:

```bash
mysql -h <host> -u <user> -p <database>
```

Useful queries:

```sql
SELECT version, description, success FROM flyway_schema_history;      -- migrations applied
SELECT u.id, u.email, u.active, r.role FROM app_user u LEFT JOIN user_role r ON r.user_id = u.id;
SELECT COUNT(*) FROM student; SELECT COUNT(*) FROM mentor_meeting;
```

Close the client afterwards — it uses one of Kitefin's 10 connections.

### Last resort only: create an Administrator with SQL

Prefer `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_RESET`. If you really must use SQL, the tables
are `app_user` and `user_role` (there is no `users` table), and the password must be a BCrypt hash:

```bash
# BCrypt hash, cost 12 (either command):
htpasswd -bnBC 12 "" 'ChooseAStrongPassword1' | tr -d ':\n'
python3 -c "import bcrypt; print(bcrypt.hashpw(b'ChooseAStrongPassword1', bcrypt.gensalt(12)).decode())"
```

```sql
INSERT INTO app_user (email, display_name, password_hash, active, mfa_enabled, failed_logins, created_at)
VALUES ('your.name@dundee.ac.uk', 'Your Name', '<bcrypt hash>', TRUE, FALSE, 0, UTC_TIMESTAMP(6));
INSERT INTO user_role (user_id, role)
SELECT id, 'ADMIN' FROM app_user WHERE email = 'your.name@dundee.ac.uk';
```

Never create or alter tables by hand — Flyway owns the schema.

---

## Corrections to the earlier troubleshooting notes

| Earlier advice | For this application |
|---|---|
| Hand-write `spring.datasource.url=jdbc:mysql://${JAWSDB_HOST}…` in `application.properties` | Not needed. The API parses `JAWSDB_URL` itself. `JAWSDB_HOST` etc. do not exist on Heroku, so that line would break the connection. |
| `spring.datasource.url=jdbc:${JAWSDB_URL}` | Do not use. It embeds the credentials in the URL; the built-in parser handles it properly. |
| `useSSL=false` | Do not use. The connection is encrypted (`sslMode=REQUIRED`), as the security requirements specify. |
| Create a `users` table and insert users with SQL | Flyway creates all 13+ tables; the user tables are `app_user` + `user_role`. The Administrator is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD`. |
| Remove the password encoder to use plain-text passwords | Never. Passwords are always BCrypt-hashed. |
| Set `SPRING_PROFILES_ACTIVE` to `default` or `prod` | Use `staging` (demo data allowed) or `prod`. Never `migrate`. |
| Set `VITE_API_BASE_URL` on the frontend | Set `API_BASE_URL` on the **web** app (runtime), plus `CORS_ALLOWED_ORIGIN` on the **API** app. |
| Use `https://mms-web.herokuapp.com` | New Heroku apps have a random suffix — always copy the real URL from `heroku apps:info`. |

---

## Quick reference — config vars

**mms-api-v1 (backend)**

| Var | Example | Required |
|---|---|---|
| `JAWSDB_URL` | set by the add-on | yes (automatic) |
| `SPRING_PROFILES_ACTIVE` | `staging` or `prod` | yes |
| `AUTH_MODE` | `local` (or `entra` once UoD IT registers the app) | yes |
| `JWT_SECRET` | `openssl rand -base64 48` | yes |
| `NOTES_ENCRYPTION_KEY` | `openssl rand -base64 32` | yes |
| `CORS_ALLOWED_ORIGIN` | `https://uod-ga-mms-web-….herokuapp.com` | yes |
| `FRONTEND_URL` | same as above (links in emails) | recommended |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | first Administrator | yes on first deploy |
| `ADMIN_RESET` | `true` once, to recover the Administrator | no |
| `SEED_DEMO_DATA` | `true` (staging only) | no |
| `DB_POOL_SIZE` | `4` on Kitefin | recommended |
| `MAIL_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | submit emails / invitations | no |

**mms-web-v1 (frontend)**

| Var | Example | Required |
|---|---|---|
| `NODE_ENV` | `production` | yes |
| `API_BASE_URL` | `https://uod-ga-mms-api-….herokuapp.com` | yes |
| `ENTRA_CLIENT_ID`, `ENTRA_TENANT_ID`, `ENTRA_API_SCOPE` | only when the API uses `AUTH_MODE=entra` | no |

**Day-to-day commands**

```bash
heroku logs --tail -a uod-ga-mms-api        # live API logs
heroku ps -a uod-ga-mms-api                 # is the web dyno up?
heroku restart -a uod-ga-mms-api            # restart (re-runs the Administrator check)
heroku releases -a uod-ga-mms-api           # history; roll back with: heroku releases:rollback
heroku config -a uod-ga-mms-api             # list config vars
```
