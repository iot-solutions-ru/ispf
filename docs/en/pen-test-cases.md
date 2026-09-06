# Pen-test case catalog (G-01)

> **Status:** Suggested grey-box cases for assessors — not an automated suite and **not** evidence of a pass.  
> Pair with [pen-test-scope.md](pen-test-scope.md) and [pen-test-prep.md](pen-test-prep.md).

Mark each case: **Pass** / **Fail** / **N/A** / **Skip** (with reason). Prefer ISPF build pinned in preflight JSON.

Legend: **AuthZ** authorization · **AuthN** authentication · **Iso** tenancy · **Inj** injection · **Cfg** misconfig.

---

## A — Authentication & session

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| A-01 | AuthN | Login with valid local user returns opaque/Bearer token usable on `/api/v1/auth/me` | `POST /api/v1/auth/login` |
| A-02 | AuthN | Invalid password → 401/403; no token leakage in body | |
| A-03 | AuthN | Token after `POST /api/v1/auth/logout` rejected on protected routes | |
| A-04 | AuthN | Expired / garbage Bearer rejected | |
| A-05 | AuthN | MFA: when `required-for-admin`, admin login without TOTP fails; with valid TOTP succeeds | Skip if MFA off |
| A-06 | AuthN | MFA bypass attempts (empty totp, replay, skew) fail | Skip if MFA off |
| A-07 | AuthN | OIDC: `/api/v1/auth/config` reflects mode; auth-code/PKCE path cannot be forced to implicit insecure flow | Skip if local-only |
| A-08 | AuthN | Session fixation / token in URL query not accepted | Console + API |
| A-09 | AuthN | Password change / reset flows (if enabled) require re-auth or token binding; no account takeover via predictable tokens | Skip if feature off |
| A-10 | AuthN | Concurrent sessions: logout of one session does not leave stale admin capabilities undocumented | Document expected behavior |

---

## B — RBAC & object ACL

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| B-01 | AuthZ | Viewer cannot `PUT`/`POST` object mutations that operator can | Role matrix in [security.md](security.md) |
| B-02 | AuthZ | Operator cannot change ACL where only admin can (`PUT …/acl`) | |
| B-03 | AuthZ | Per-variable `readRoles` / `writeRoles` deny cross-role read/write | BL-154 |
| B-04 | AuthZ | History/export endpoints respect variable ACL | |
| B-05 | AuthZ | Event/function `invokeRoles` denied for unauthorized role | |
| B-06 | AuthZ | Unauthenticated access to `/api/v1/objects` fails | |
| B-07 | AuthZ | IDOR: object path of another user’s private tree not readable by guessing path | |
| B-08 | AuthZ | Audit log / security-user admin APIs denied for non-admin | |

---

## C — Multi-tenant isolation

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| C-01 | Iso | Tenant A admin cannot list/read Tenant B objects | Need two tenants |
| C-02 | Iso | Historian samples for Tenant B paths not returned to Tenant A token | |
| C-03 | Iso | Audit export does not include other tenant events (when tenancy on) | |
| C-04 | Iso | Switching `tenant` header/claim cannot escalate to foreign tenant | |
| C-05 | Iso | RLS mode: direct DB checks out of band optional white-box add-on | N/A for black-box |

---

## D — Web Console / browser

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| D-01 | Inj | Stored XSS in object displayName / description not executed as admin HTML | |
| D-02 | Inj | Reflected XSS on search/filter params | |
| D-03 | AuthZ | UI hides admin buttons for viewer **and** API still denies | UI-only hide ≠ security |
| D-04 | Cfg | Token storage: document localStorage risk; no token in logs/crash dumps | Informational |
| D-05 | AuthN | CSRF on state-changing cookie auth — N/A if Bearer-only; verify no cookie session fallback | |

---

## E — WebSocket / realtime

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| E-01 | AuthN | WS connect without token rejected | |
| E-02 | AuthZ | Subscribe to path outside ACL rejected / no data | |
| E-03 | Iso | Tenant A subscription never receives Tenant B updates | |

---

## F — Historian / analytics

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| F-01 | AuthZ | History query for unauthorized variable fails or empty per policy | |
| F-02 | Iso | Cross-tenant history IDOR | |
| F-03 | Cfg | Analytics multi-tag query cannot be used to exfiltrate unauthorized tags | |
| F-04 | Cfg | Excessive `limit` / huge windows handled without stack disclosure | |

---

## G — Platform & misconfig

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| G-01c | Cfg | `/actuator/**` (if exposed) does not leak env secrets; health is expected public | Name `G-01c` to avoid clash with gap G-01 |
| G-02c | Cfg | Debug / Swagger UI not exposed on demostand prod profile without auth | |
| G-03c | Cfg | Default `admin/admin` **must not** remain on internet-facing demostand without documented lab exception | Honesty: demostand may use lab defaults — record as accepted risk or finding |
| G-04c | Cfg | Driver pack upload rejects unsigned/malicious zip when signing enforced | Skip if feature off |
| G-05c | Inj | SQL/NoSQL injection on search and history filters | |
| G-06c | Cfg | Security headers baseline (CSP/frame-ancestors) — informational for SPA | |
| G-07c | Cfg | Error bodies do not leak stack traces / JDBC URLs / secrets on demostand prod profile | |

---

## H — Abuse / resilience (lab / staging only)

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| H-01 | Cfg | Auth brute-force: lockout / rate limit behavior documented | Soft on demostand |
| H-02 | Cfg | Large JSON body rejected with clean error | Staging |
| H-03 | Cfg | Plugin upload zip-bomb / path traversal | Staging only |

---

## I — Audit & export surfaces

| ID | Class | Case | Notes / hints |
|----|-------|------|----------------|
| I-01 | AuthZ | Audit export requires appropriate role; viewer denied | |
| I-02 | Iso | Audit export cannot pull foreign-tenant events when tenancy on | Overlaps C-03 |
| I-03 | Cfg | Bulk export / backup endpoints (if present) are authz-gated and not world-readable | Skip if absent |

---

## Coverage map (quick)

| Surface in scope doc | Primary cases |
|----------------------|---------------|
| HTTP API | A-*, B-*, C-*, F-*, G-*, I-* |
| Web Console | D-* |
| WebSocket | E-* |
| Historian / CH | F-* |
| OIDC / MFA | A-05…A-07, A-09 |
| Pack upload | G-04c, H-03 |
| Audit / export | I-*, B-08 |

Results matrix template: [`case-results.template.md`](../evidence/security-pentest/case-results.template.md).

RU summary: [pen-test-prep.md](../ru/pen-test-prep.md) (cases stay EN-canonical for assessors).
