# Jewellery Shop ERP

A responsive ERP foundation for a jewellery retail business: authentication,
role and permission based authorisation, master data, per-piece inventory and
shop settings — built so that billing, GST, sales, purchases and the rest can be
added as modules rather than as a rewrite.

---

## Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Technology stack](#technology-stack)
4. [Prerequisites](#prerequisites)
5. [PostgreSQL setup](#postgresql-setup)
6. [Environment variables](#environment-variables)
7. [Running the backend](#running-the-backend)
8. [Running the frontend](#running-the-frontend)
9. [Default administrator bootstrap](#default-administrator-bootstrap)
10. [Running the tests](#running-the-tests)
11. [API documentation](#api-documentation)
12. [Folder structure](#folder-structure)
13. [Security model](#security-model)
14. [Data model notes](#data-model-notes)
15. [Future module strategy](#future-module-strategy)
16. [Assumptions and known gaps](#assumptions-and-known-gaps)

---

## Overview

The first release covers:

| Area | What it does |
|---|---|
| Authentication | JWT access tokens with revocable, rotating refresh tokens |
| Authorisation | Per-module, per-action permissions enforced on every endpoint |
| Users | Create, edit, activate, deactivate, reset password, assign permissions |
| Masters | Item types, categories, sub categories, HSN codes, purities |
| Inventory | Individual jewellery pieces with serial number, purity and weight |
| Shop settings | The shop profile used on invoices, receipts and GST reports |
| Dashboard | Summary counters, filtered by what the signed-in user may see |

---

## Architecture

**Modular monolith, REST-separated.**

```
Angular SPA  ──HTTPS/JSON──►  Spring Boot API  ──JDBC──►  PostgreSQL
  JWT in memory                 stateless, JWT              Flyway-managed
  refresh in storage            @PreAuthorize gates
```

Each business capability is a package under `com.jewellery.erp` containing its
own controller, service, repository, entity, DTOs and mapper. Modules talk to
each other through **service interfaces and DTOs only** — never by reaching into
another module's repository or entity internals. That boundary is what allows a
module to be extracted later without unpicking the data access layer.

Layer responsibilities are strict:

| Layer | Does | Never does |
|---|---|---|
| Controller | HTTP mapping, DTO binding, `@PreAuthorize` | business logic, transactions |
| Service | business rules, `@Transactional`, orchestration | HTTP concerns |
| Repository | Spring Data JPA, specifications | business rules |
| Entity | persistence model | leave the service layer |

### Decisions worth knowing

1. **Access token (15 min) + DB-backed refresh token (7 days).** Pure stateless
   JWT has no real logout. Only the SHA-256 hash of the refresh token is stored,
   and it rotates on every use, so a stolen token is usable at most once and its
   reuse is detectable. Logout, deactivation, password reset and permission
   changes all revoke refresh tokens, which is what makes them take effect
   promptly instead of at the end of a multi-day session.
2. **Authorities are permission codes, not roles.** Endpoints are guarded with
   `hasAuthority('CATEGORY_CREATE')`. Roles are just bundles of permissions.
3. **Errors are the only wrapped responses.** Success payloads are the DTO
   itself; pagination uses an explicit `PageResponse<T>` so the client never
   depends on Spring's internal `Page` JSON shape.
4. **Soft delete everywhere,** with a referential guard: deactivation is always
   allowed, hard delete is refused once anything references the record.
5. **`BigDecimal` / `NUMERIC` for every weight, rate and percentage.** No
   `double` appears anywhere in the money or weight paths.
6. **Hand-written mappers**, not MapStruct — no annotation-processor ordering to
   debug, and `UserMapper` is the single readable guarantee that a password hash
   never reaches a response.

---

## Technology stack

**Backend** — Java 21 · Spring Boot 3.3 · Spring Web · Spring Data JPA · Spring
Security · Bean Validation · jjwt 0.12 · Flyway · springdoc-openapi 2.6 ·
PostgreSQL 14+ · Maven · JUnit 5 · Mockito · AssertJ

**Frontend** — Angular 20 (standalone components, signals, functional guards and
interceptors) · TypeScript 5.8 (strict) · Reactive Forms · SCSS

---

## Prerequisites

| Tool | Version | Check with |
|---|---|---|
| JDK | 21 or newer | `java -version` |
| Maven | 3.9 or newer | `mvn -version` |
| Node.js | 20.19+, 22.12+ or 24 | `node --version` |
| npm | 10 or newer | `npm --version` |
| PostgreSQL | 14 or newer | `psql --version` |

---

## PostgreSQL setup

Create the database and an owner role. Flyway creates every table on first
start-up — do not create them by hand.

```bash
psql -U postgres -c "CREATE ROLE jewellery WITH LOGIN PASSWORD 'change-me';"
psql -U postgres -c "CREATE DATABASE jewellery_erp OWNER jewellery;"
```

On Windows without `psql` on the PATH, use pgAdmin and run the same two
statements in a query window.

Migrations live in `backend/src/main/resources/db/migration`:

| Migration | Contents |
|---|---|
| `V1__initial_schema.sql` | every table, key, index and check constraint |
| `V2__seed_roles_and_permissions.sql` | the permission catalogue and the two roles |
| `V3__seed_shop_settings.sql` | the singleton shop row |
| `V4__seed_master_defaults.sql` | starter item types, purities, categories, HSN codes |

`V4` is convenience data, all of it editable from the UI. Delete the file before
your first deployment if the shop prefers to key in its own masters.

Hibernate runs with `ddl-auto=validate`: it verifies that the mapped model
matches the migrated schema and never changes it. A mismatch fails start-up
loudly, which is the point.

### Shortcut for local testing

`database/full-setup-dev.sql` does all of the above in one run — role, database,
every table, the reference data, **and two working sign-in accounts** — so the
application can be opened and exercised without any manual steps:

```bash
psql -U postgres -f jewellery-erp/database/full-setup-dev.sql
```

| Username | Password    | Access |
|---|---|---|
| `admin`  | `Admin@123` | Everything |
| `staff`  | `Staff@123` | Counter role: inventory, new sales, customers, old gold purchase, report preview — cannot cancel invoices or export Excel |

Because the tables already exist, start the backend **once** with a Flyway
baseline so it does not try to re-create them:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.flyway.baseline-on-migrate=true -Dspring.flyway.baseline-version=21"
```

**This file is for development only.** It is the deliberate exception to the
rule that no credential lives in source control, and the passwords in it are
public to anyone who can read the repository. For a real shop, ignore it: let
Flyway build the schema and let the application create the first administrator
from the `INITIAL_ADMIN_*` variables, as described below.

---

## Environment variables

Copy `.env.example` and fill it in. Nothing has a usable default — in
particular the application refuses to start without a JWT secret rather than
falling back to a well-known one.

```bash
cp .env.example .env
```

| Variable | Required | Notes |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | yes | PostgreSQL connection |
| `JWT_SECRET` | yes | Base64, ≥ 256 bits — `openssl rand -base64 48` |
| `JWT_ISSUER` | no | defaults to `jewellery-erp` |
| `JWT_ACCESS_TOKEN_VALIDITY` | no | ISO-8601 duration, default `PT15M` |
| `JWT_REFRESH_TOKEN_VALIDITY` | no | default `P7D` |
| `CORS_ALLOWED_ORIGINS` | yes in prod | comma separated; wildcards rejected under `prod` |
| `INITIAL_ADMIN_USERNAME` / `_PASSWORD` / `_EMAIL` | first run only | see below |

Rotating `JWT_SECRET` invalidates every issued access token — the intended
emergency response to a leak.

---

## Running the backend

On this machine, everything is wired up already — build the jar, then start it:

```bash
tools/apache-maven-3.9.9/bin/mvn.cmd -f backend package -DskipTests
```

```bash
powershell -ExecutionPolicy Bypass -File start-backend.ps1
```

`start-backend.ps1` sets JAVA_HOME, the database URL and the Flyway baseline,
then serves on <http://localhost:8080>. Rebuild the jar after any backend code
change.

On a new machine it will stop and ask for `secrets.local.ps1`. Copy the example
and fill it in — the database password and the JWT signing key are kept out of
version control, because a signing key in a repository is a signing key
everyone has:

```bash
cp secrets.local.ps1.example secrets.local.ps1
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

Elsewhere, the ordinary commands apply:

```bash
cd backend
mvn spring-boot:run
```

### Windows: "Unable to establish loopback connection"

If Tomcat refuses to start with this:

```
java.io.IOException: Unable to establish loopback connection
    at sun.nio.ch.PipeImpl$Initializer$LoopbackConnector.run
Caused by: java.net.SocketException: Invalid argument: connect
```

the JDK cannot create the AF_UNIX socket it uses for the NIO selector's wakeup
pipe. On Windows that socket is placed in `java.io.tmpdir` by default, and some
machines block socket creation under `%LOCALAPPDATA%\Temp` — security software
and Controlled Folder Access are the usual culprits. It affects every Java
server on that machine, not just this one.

Point the JDK somewhere else:

```
-Djdk.net.unixdomain.tmpdir=<a directory outside %LOCALAPPDATA%\Temp>
```

`start-backend.ps1` already does this, using the project's `.tmp` directory.
A bare `Selector.open()` is enough to reproduce the failure if you want to
confirm it independently of the application.

---

## Running the frontend

```bash
cd frontend
npm install
npm start
```

The application starts on <http://localhost:4200> and calls the API at
`http://localhost:8080/api` (see `src/environments/environment.development.ts`).

Production build:

```bash
cd frontend
npm run build
```

The output in `dist/jewellery-erp` is static — serve it from nginx, Caddy or the
same reverse proxy as the API. The production environment file points at `/api`
on the same origin, which removes CORS from the picture entirely.

---

## Default administrator bootstrap

There is **no seeded password anywhere in this repository** — not in plain text
and not as a hash. A SQL file containing a credential is a credential in version
control, and every installation would ship with the same one.

Instead, on start-up, `InitialAdminBootstrap` checks whether any active
administrator exists. If none does, it creates one from the environment:

```bash
export INITIAL_ADMIN_USERNAME=admin
export INITIAL_ADMIN_PASSWORD='choose-a-strong-one'
export INITIAL_ADMIN_EMAIL=owner@example.com
```

The password is hashed with BCrypt (strength 12) before storage and never
appears in the logs. The account is created with **must change password** set, so
the first sign-in goes straight to the change-password screen and no further
until a new password is chosen.

Afterwards, remove the variables from the environment (or set
`INITIAL_ADMIN_ENABLED=false`). The runner is inert once an administrator
exists, so leaving them set changes nothing — it is simply good hygiene not to
keep a password in the deployment configuration.

---

## Running the tests

```bash
cd backend && mvn test
cd frontend && npm test
```

The backend suite is unit and web-layer only; it needs no database or Docker.
What it covers:

| Test | Asserts |
|---|---|
| `InventoryItemServiceTest` | duplicate serial rejected · leading zeros preserved · short serials padded · non-numeric and over-length rejected · purity must match item type · sub category must match category · weight stored exactly · next-serial suggestion |
| `PurityServiceTest` | a purity is usable only with its own item type · inactive purities refused · same fineness allowed across item types, refused within one · no re-parenting once referenced |
| `SubCategoryServiceTest` | parent category mandatory and must be active · name unique within the category only · inactive and foreign sub categories refused · no re-parenting once referenced |
| `ItemTypeServiceTest` | input normalised before comparison · duplicates refused case-insensitively · lookups return active records only · referenced records cannot be deleted but can be deactivated |
| `UserServiceTest` | no self-deactivation · last administrator protected · passwords BCrypt-hashed, never stored plain · deactivation and reset revoke sessions · users with history are deactivated, not deleted |
| `JwtTokenProviderTest` | claims round-trip · tampered, foreign-signed, expired, wrong-issuer and malformed tokens all rejected · weak secret fails at start-up |
| `InventoryEndpointSecurityTest` | 401 without a token · 403 with the wrong permission and the service never reached · 200/204 with the right one · field-level 400s for a bad serial, zero weight and missing references |

Frontend specs cover the server-error-to-form mapping and the weight formatting.

---

## API documentation

Swagger UI is enabled by the `dev` profile only:

- UI — <http://localhost:8080/swagger-ui.html>
- Spec — <http://localhost:8080/v3/api-docs>

Under `prod` both the UI and `/v3/api-docs` are disabled, so the documentation
endpoints return 404 even though the security rules permit them. To expose them
in a controlled way, enable springdoc in `application-prod.yml` and put the two
paths behind your reverse proxy's authentication — do not open them publicly.

Every operation's description names the permission it requires.

### Endpoint summary

| Method | Path | Permission |
|---|---|---|
| POST | `/api/auth/login` · `/refresh` | public |
| POST | `/api/auth/logout` · `/logout-all` | authenticated |
| GET | `/api/auth/me` | authenticated |
| GET POST | `/api/item-types` | `ITEM_TYPE_VIEW` / `_CREATE` |
| GET PUT PATCH DELETE | `/api/item-types/{id}` `…/status` | `ITEM_TYPE_VIEW` / `_EDIT` / `_DELETE` |
| GET | `/api/item-types/lookup` | any of item type / purity / inventory VIEW |
| … | `/api/categories`, `/api/hsn-codes` | identical shape |
| GET | `/api/sub-categories?categoryId=` | `SUB_CATEGORY_VIEW` |
| GET | `/api/sub-categories/lookup?categoryId=` | sub category or inventory VIEW |
| GET | `/api/purities?itemTypeId=` | `PURITY_VIEW` |
| GET | `/api/purities/lookup?itemTypeId=` | purity or inventory VIEW |
| GET | `/api/inventory/items?…&page=&size=&sort=` | `INVENTORY_VIEW` |
| GET | `/api/inventory/items/next-serial` | `INVENTORY_CREATE` |
| GET | `/api/inventory/items/by-serial/{serial}` | `INVENTORY_VIEW` |
| POST PUT PATCH DELETE | `/api/inventory/items…` | `INVENTORY_CREATE` / `_EDIT` / `_DELETE` |
| GET POST PUT PATCH DELETE | `/api/users…` | `USER_VIEW` / `_CREATE` / `_EDIT` / `_DELETE` |
| GET PUT | `/api/users/{id}/permissions` | `USER_VIEW` / `USER_EDIT` |
| POST | `/api/users/me/change-password` | authenticated |
| GET | `/api/permissions` · `/grouped` · `/api/roles` | `USER_VIEW` |
| GET PUT | `/api/shop-settings` | `SHOP_SETTINGS_VIEW` / `_EDIT` |
| GET | `/api/dashboard/summary` | authenticated |

### Error format

Every failure returns the same envelope:

```json
{
  "timestamp": "2026-09-15T09:31:22.417Z",
  "status": 409,
  "error": "Conflict",
  "message": "Serial number 123456 already exists.",
  "path": "/api/inventory/items",
  "fieldErrors": { "serialNumber": "Serial number 123456 already exists." }
}
```

`fieldErrors` is what lets the client render a server-side rejection next to the
control that caused it. Stack traces and JDBC messages never reach the client.

---

## Folder structure

```
jewellery-erp/
├── .env.example
├── README.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/jewellery/erp/
│       │   ├── common/        config · dto · entity · exception · repository · util
│       │   ├── security/      SecurityConfig · JWT · principal · auditing · handlers
│       │   ├── auth/          login, refresh, logout, refresh tokens
│       │   ├── user/          accounts, status, passwords, permission grants, bootstrap
│       │   ├── role/ permission/
│       │   ├── itemtype/ category/ subcategory/ hsn/ purity/
│       │   ├── inventory/     entity · spec · service · controller
│       │   ├── shop/ dashboard/
│       │   └── JewelleryErpApplication.java
│       ├── main/resources/    application*.yml · db/migration/V1..V4
│       └── test/java/…
└── frontend/
    ├── angular.json · package.json · tsconfig*.json
    └── src/
        ├── environments/
        ├── styles/            _tokens.scss · _base.scss · _components.scss
        └── app/
            ├── core/          auth · guards · interceptors · services
            ├── layout/        shell · header · sidebar · nav.config
            ├── shared/        components · directives · pipes · models · utils
            └── features/      auth · dashboard · masters · inventory · shop-settings
```

Each module keeps its own controller / service / repository / entity / dto /
mapper. Frontend features are lazily loaded, one chunk each.

---

## Security model

### Authentication

```
login ──► BCrypt verify (strength 12) ──► active & unlocked check
      ──► access JWT (15 min; claims: sub, uid, name, roles, perms)
      ──► refresh token (256-bit random, SHA-256 hash stored)

request ──► JwtAuthenticationFilter ──► signature + issuer + expiry
        ──► principal rebuilt from claims (no database hit)

401 ──► Angular error interceptor ──► single-flight refresh ──► replay
    ──► if the refresh fails: session cleared, back to /login with the URL kept
```

### Authorisation

Effective permissions = **role permissions ∪ direct per-user grants**.

- `ROLE_ADMIN` is seeded with every permission.
- `ROLE_USER` is seeded with **none**, deliberately. A new staff member can sign
  in and see the dashboard, and nothing else, until an administrator grants
  permissions on the Users → Permissions screen. This means adding a module to
  the system never silently widens staff access.
- There are no "deny" rows. With `ROLE_USER` carrying nothing, additive grants
  already express any staff access, and deny rules are the classic source of
  authorisation behaviour nobody can explain.

The backend is the enforcement point:

```java
@PreAuthorize("hasAuthority('INVENTORY_CREATE')")
```

The Angular client hides the same actions — route guards, and the
`*appHasPermission` structural directive — but that is a convenience so users are
not offered actions that will fail. `InventoryEndpointSecurityTest` calls the
endpoints directly to prove the server decides.

**Timing.** A permission change is embedded in the next access token, so it
lands within 15 minutes at worst. Because `PUT /users/{id}/permissions` also
revokes that user's refresh tokens, it usually lands on their next request.

### Token storage in the browser

| Token | Where | Why |
|---|---|---|
| Access token | memory only | the credential that opens every endpoint — an XSS payload gets at most a 15-minute window |
| Refresh token | `localStorage` | so a page reload does not sign the user out; revocable server-side and rotated on every use |

The stronger option is an HttpOnly, SameSite cookie for the refresh token. That
requires the API and SPA on one origin plus CSRF handling — a deployment
decision this release does not force. The trade-off is recorded here rather than
left implicit.

### Other measures

- BCrypt strength 12; passwords are never logged, returned, or placed in any DTO.
- An unknown username is reported as bad credentials, so login cannot be used to
  enumerate accounts.
- Deny-by-default routing: `anyRequest().authenticated()`, so a new endpoint is
  protected the moment it is written.
- CSRF is disabled because the API never authenticates from an ambient cookie.
- CORS origins are explicit; a wildcard is rejected outright under `prod`.
- HSTS, `X-Content-Type-Options`, `X-Frame-Options: DENY`, referrer policy.
- Validation runs three times: Angular for immediacy, Bean Validation plus
  service rules on the server for correctness, and database constraints as the
  final authority. Each layer exists because the one above it can be bypassed.

---

## Data model notes

**Jewellery is not quantity-based stock.** Two 22K rings of the same design have
different weights and are different objects, so "5 units of SKU 123" cannot
describe them. Each row in `inventory_items` is **one physical piece**, which is
what lets a future sale, return or old-gold exchange reference the exact piece
that moved.

Consequences the rest of the design depends on:

- `serial_number` is `VARCHAR(6)`, unique, `CHECK (serial_number ~ '^[0-9]{6}$')`.
  Text, not a number, because `000001` and `1` are different serials and leading
  zeros must survive. The service pads a short entry to its canonical form so the
  same piece cannot be entered twice under two spellings.
- `weight_grams` is `NUMERIC(12,3)` with `CHECK (> 0)`. Gold is priced per gram;
  a floating-point milligram error becomes a rupee error on every invoice.
- `purity` belongs to an item type, and `sub_category` to a category. Both
  relationships are re-validated on every inventory write — a foreign key cannot
  express a cross-column rule, and the frontend cascade is a convenience, not a
  guarantee.
- `sub_category_id` and `hsn_id` are nullable: not every category is subdivided,
  and HSN is only needed at billing time.
- Master records are deactivated, not deleted. A deactivated master stays on the
  inventory that references it and disappears from every dropdown, because lookup
  endpoints return active records only.

Audit columns (`created_at/by`, `updated_at/by`) are on every business table.
`created_by` stores the **username string**, not a foreign key: an audit trail
must survive the deletion or rename of the account that produced it, and must
never require a join to be readable.

---

## Future module strategy

Nothing below is implemented. The current design is what makes each of them
additive rather than structural:

| Future module | What already supports it |
|---|---|
| Sales, invoicing, returns | each piece is individually identified, so a document can point at the exact item |
| Old gold purchase / exchange | same — an incoming piece becomes a new inventory row |
| GST reports | HSN and rate are exact decimals; shop GSTIN is stored and validated |
| Gold / silver rate management | purity is a master with a numeric fineness, so rate × fineness × weight is already expressible |
| Making charges, wastage, stone charges | additive columns or a child table on `inventory_items` |
| Stock ledger, movement, adjustments | append-only tables keyed on `inventory_items.id` |
| Barcode / QR | the six-digit serial is the natural payload |
| Audit log module | `AuditableEntity` is already the seam; add an entity listener |
| Role management | `role_permissions` exists and is already read by the UI |
| Multi-branch | `shop_settings` is a table with a singleton constraint, not configuration — lift the constraint and add a branch key |

Two changes will be wanted early and are deliberately not pre-built:

- **`inventory_items.status`** (`IN_STOCK` / `SOLD` / `RESERVED`) — sales will
  need it. Today there is only `active`. Adding it is an additive migration.
- **A per-category size master** — `size` is free text for now, which keeps every
  category expressible without inventing a taxonomy the shop has not asked for.

---

## Assumptions and known gaps

Documented rather than hidden:

1. **Serial numbers are entered by hand**, with a "suggest next" helper. Six
   digits caps the shop at 1,000,000 pieces ever — fine for a single shop, worth
   revisiting before multi-branch.
2. **The suggested serial is not a reservation.** Two people opening the form at
   once see the same number; the second save is rejected with a clear message.
   Reserving would leave permanent gaps whenever a form is abandoned.
3. **One role per user in the UI.** The data model is many-to-many and the API
   accepts a list; the form offers a single selector because nothing in this
   release needs more.
4. **No e-mail delivery.** A reset password is handed over directly. There is no
   forgotten-password flow, because that needs mail infrastructure the shop may
   not have.
5. **Dark theme follows the operating system** and has no in-app toggle.
6. **Tests do not touch a database.** Service rules are covered with mocks and
   the API layer with `@WebMvcTest`. Repository queries and the Flyway migrations
   are exercised the first time the application starts — a Testcontainers
   integration suite is the natural next addition.
7. **Swagger is dev-only.** See [API documentation](#api-documentation) for how
   to expose it safely elsewhere.
