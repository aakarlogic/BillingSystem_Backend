# Pavti API (पावती) — Java Spring Boot backend

Backend for the Pavti billing app. It has the same layers as the Health 360 C# backend
(Controller → BAL → DAL → MySQL stored procedures), rewritten in Java 17 + Spring Boot 3.3.

```
React (D:\Pavti\pavti)  ──POST /api/...──▶  Spring Boot (D:\Pavti\pavti-api)  ──CALL sp_...──▶  MySQL 8
```

See [docs/KNOWLEDGE.md](docs/KNOWLEDGE.md) for the full guide: how login and page rights work, how
a bill is calculated, and how to add a new screen's API.

---

## 1. Run it on your computer

**You need:** Java 17 (JDK), MySQL 8. Maven is not needed; the project ships `mvnw`.

### Step 1 — put your MySQL password in `local.yml` (once)

Open `local.yml` (next to `pom.xml`) and set `password:` to **your MySQL root password**.
That is the only change needed. The file is in `.gitignore`, so it never goes to git.
(`local.yml.example` is a clean copy if you ever need one.)

You don't create the database or tables yourself. On first start:
- the `pavti` database is created (`createDatabaseIfNotExist=true`) and set to `utf8mb4_unicode_ci`
- **Flyway** runs every file in `src/main/resources/db/migration`, which creates all tables, master
  data (roles, pages, rights, plans) and stored procedures
- the first platform admin is created: mobile **9000000000**, password from `local.yml` (`admin12345`)

> For a server, use environment variables instead of `local.yml`: `PAVTI_DB_USER`,
> `PAVTI_DB_PASSWORD`, `PAVTI_JWT_SECRET`, `PAVTI_ADMIN_PASSWORD` (table below), and a separate
> `pavti_app` MySQL user instead of root.

### Step 2 — start it

**IntelliJ IDEA:** *File → Open →* `D:\Pavti\pavti-api` (open the folder, so it loads `pom.xml` as a
Maven project). *File → Project Structure → SDK* = **Java 17**. Open `PavtiApiApplication.java` and
press the green ▶ next to `main`.

**Or the command line:**

```powershell
cd D:\Pavti\pavti-api
.\mvnw.cmd spring-boot:run
```

You'll see `Started PavtiApiApplication` in the log.

- API: http://localhost:8080/api
- All APIs, clickable (Swagger): http://localhost:8080/swagger-ui.html

### If it does not start

| Message in the log | Fix |
|---|---|
| `Port 8080 was already in use` | another copy is already running. Stop it (red ■ in IntelliJ), or set `server.port: 8081` in `local.yml` |
| `Access denied for user 'root'@'localhost'` | wrong password in `local.yml` |
| `Communications link failure` | MySQL isn't running. Start the **MySQL80** service (Windows Services) |
| Aiven: `Access denied for user 'avnadmin'` | wrong password in `local.yml` (copy it again from the Aiven console) |
| Aiven: `Communications link failure` / SSL errors | keep `sslMode=REQUIRED` in the URL; check the Aiven service is **Running** (free services sleep when unused) |
| Aiven: `Too many connections` | keep `hikari.maximum-pool-size: 5` in `local.yml` |
| `release version 17 not supported` / red code everywhere | IntelliJ SDK isn't Java 17 (Project Structure → SDK) |
| `Validate failed: Migration checksum mismatch` | a migration file was edited after it ran. Never edit V1–V7; add a new V8 file |

### Step 3 — connect the React app

In `D:\Pavti\pavti\.env`:

```
VITE_USE_MOCK_API=false
VITE_API_BASE_URL=http://localhost:8080/api
```

Then `npm run dev` in `D:\Pavti\pavti`. The URLs and JSON shapes match `src/services/*Service.ts`
and `src/types/*`.

### All settings

| Env variable | Default | What it is |
|---|---|---|
| `PAVTI_DB_HOST` / `PAVTI_DB_PORT` / `PAVTI_DB_NAME` | localhost / 3306 / pavti | database |
| `PAVTI_DB_USER` / `PAVTI_DB_PASSWORD` | pavti_app / (empty) | database login |
| `PAVTI_JWT_SECRET` | dev-only text | signs login tokens; **must** be set in production |
| `PAVTI_ACCESS_MINUTES` | 720 | login token life (12 h = one shift) |
| `PAVTI_REFRESH_DAYS` | 30 | how long "stay signed in" lasts |
| `PAVTI_OTP_DEV_MODE` | true | true = OTP is shown in the response/log instead of SMS. Set **false** in production |
| `PAVTI_ADMIN_MOBILE` / `PAVTI_ADMIN_PASSWORD` | 9000000000 / (empty) | first platform admin, created only if none exists |
| `PAVTI_CORS_ORIGINS` | localhost:5180, 5173 | websites allowed to call the API |
| `PAVTI_CRYPTO_MODE` | optional | API body encryption: `optional` (also plain, for Swagger) · `required` (production) · `off` |
| `PAVTI_RSA_PRIVATE_KEY` | (empty) | server key as PEM text; empty = `keys/pavti_rsa_private.pem`, created by itself |
| `PORT` | 8080 | API port |

### Tests

```powershell
.\mvnw.cmd test
```

`BillingCalculatorTest` runs the same bill-math cases as the React `billing.test.ts`, so both sides
always agree.

---

## 2. Folder structure

```
pavti-api/
├── pom.xml                         libraries (like the C# .csproj)
├── mvnw, mvnw.cmd                  Maven wrapper — no install needed
├── docs/KNOWLEDGE.md               the full guide
└── src/main/
    ├── resources/
    │   ├── application.yml         settings (like appsettings.json)
    │   └── db/migration/           SQL run by Flyway, in order, once each
    │       ├── V1__schema.sql          all tables (5 audit columns, no foreign keys)
    │       ├── V2__master_data.sql     roles, pages, role page rights, staff ticks, plans
    │       ├── V3__sp_core.sql         login, sessions, OTP, menu rights, audit, error log
    │       ├── V4__sp_platform.sql     businesses, plans, users
    │       ├── V5__sp_restaurant.sql   menu groups, dishes, tables, customers
    │       ├── V6__sp_billing.sql      orders, bills, payments, reports
    │       └── V7__sp_locks_and_fixes.sql  row locks for money, small fixes
    └── java/com/pavti/
        ├── PavtiApiApplication.java    start point (Program.cs)
        ├── config/       AppProperties, WebConfig (CORS + interceptor), OpenApiConfig, AdminBootstrap
        ├── controller/   one per screen area: URL → BAL. No logic here.     (C#: Controllers)
        ├── bal/          business rules, permissions, bill math             (C#: BusinessAccessLayer)
        ├── dal/          calls stored procedures, maps rows → records       (C#: DataAccessLayer)
        ├── dataobject/   request/response shapes, as Java records           (C#: DataObject)
        ├── crypto/       request/response encryption (RSA + AES-GCM), see KNOWLEDGE 3a
        ├── filter/       @ValidateToken, @RequirePage, @RequirePermission,
        │                 @RequireRole, TokenInterceptor, CurrentUser        (C#: ValidateTokenAttribute)
        └── common/       ApiResponse, ApiException, GlobalExceptionHandler,
                          JwtTokenHelper, PasswordHelper, TextHelper, BusinessDateHelper
```

### API list (all `POST`, all return `{ Success, StatusCode, Message, Data }`)

| Area | URLs under `/api` | Who |
|---|---|---|
| Encryption | `Crypto/GetPublicKey` (public, never encrypted) | everyone |
| Auth | `Auth/Login, SendOtp, Register, ResetPassword, Refresh, Logout, Me, ChangePassword, UpdateProfile` | everyone |
| Menu rights | `Menu/GetMenuBinding` | signed in |
| Businesses | `Business/GetAll, Save, SetStatus, GetStats` | platform admin |
| Settings | `Business/GetSettings` (team), `Business/SaveSettings` (owner) | business |
| Plans | `Plan/GetAll` (public), `Plan/Save, Delete` | platform admin |
| Team | `User/GetAll, Save, SetStatus` | owner (own team), admin (all) |
| Menu | `MenuItem/GetCategories, GetAll` (team), `SaveCategory, DeleteCategory, Save, SetAvailability` (owner) | business |
| Tables | `Table/GetFloor, Add, Remove` | business |
| Customers | `Customer/GetAll, Save, Delete` | business |
| Orders/Bills | `Order/GetAll, GetById, Save, MakeBill, Cancel` | ORDERS_TAKE / BILLS_CANCEL |
| Payments | `Payment/GetAll, Save` (PAYMENTS_TAKE), `Payment/Refund` (owner) | business |
| Reports | `Report/GetDashboard, GetSales` | HOME / REPORTS page |
| History | `Audit/GetAll` | owner, admin |

---

## 3. What was taken from Health 360 and what was left out

**Kept (same idea, Java form):** Controller → BAL → DAL layers, stored procedures for all SQL,
`DataClass` that runs procedures and maps rows by column name, the `{Success, StatusCode, Message, Data}`
response, a token check attribute, page rights from the database, error log table, API request log.

**Changed:** RSA + AES request encryption is kept, but with AES-GCM, RSA-OAEP-SHA256 and no private key in the code.

**Left out (not needed for Pavti):** Firebase
push, iText PDF (the React app prints bills), SMTP / Microsoft Graph email, NAS file storage,
Excel upload, booking tokens, every Health 360 module (patients, doctors, labs …), scratch folders.

**Added:** BCrypt passwords, JWT with server-side sessions (logout works immediately), refresh
tokens, lockout after 5 wrong passwords, OTP sign-up, staff permission ticks, multi-business
data separation (`business_id` always from the token), row locks for bill numbers and payments,
Flyway migrations, Swagger, one place for errors (`GlobalExceptionHandler`).
