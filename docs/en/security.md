> **Language:** Canonical English. Russian edition: [ru/security.md](../ru/security.md).

# Security and RBAC

## Model

ISPF uses **role-based access** at the HTTP API level:

| Role | Spring authority |
|------|------------------|
| `admin` | `ROLE_admin` |
| `developer` | `ROLE_developer` |
| `operator` | `ROLE_operator` |

Per-object ACL — see [security](security.md) (`object_acl_entries`, **Access** tab in Web Console).

## Authentication profiles

### local

File: `application-local.yml`

- OAuth disabled (dummy issuer)
- RBAC enabled; authentication via **Bearer token** after `POST /api/v1/auth/login`
- `ispf.security.token-auth-enabled: true`
- `ispf.security.local-default-role:` empty — no token means access denied
- `LocalBearerTokenFilter` + optional `X-ISPF-Role` fallback (dev only, **disabled by default** — `ispf.security.local-role-header-enabled`)

Default accounts (if DB is empty): `admin/admin`, `developer/developer`, `operator/operator`.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/objects
```

Web Console: login screen; session stored in `localStorage`. In `dev`/prod profile — **OIDC authorization code + PKCE** via Keycloak (**Sign in with Keycloak**). Configuration: `GET /api/v1/auth/config`. Admins manage users in tree `root.platform.security.users`.

**App auto-start:** a user can enable `autoStartEnabled` and set `autoStartApp` (operator app id, list — `GET /api/v1/operator-apps`). After Web Console login, the operator app opens instead of the admin console.

### User management (admin)

| Endpoint | Description |
|----------|----------|
| `GET /api/v1/security/users` | List users |
| `POST /api/v1/security/users` | Create user |
| `PUT /api/v1/security/users/{username}` | Update (roles, enabled, displayName, **autoStartEnabled**, **autoStartApp**) |
| `DELETE /api/v1/security/users/{username}` | Delete |
| `POST /api/v1/security/users/{username}/password` | Change password |
| `POST /api/v1/auth/logout` | End session |
| `GET /api/v1/auth/me` | Current user (with token) |
| `GET /api/v1/auth/config` | Auth mode (`local` / `oidc`) for Web Console |

Users and roles sync into the object tree (see [object-model](object-model.md)).

### dev/default (as in production)

- OAuth2 resource server, JWT from Keycloak
- Issuer: `http://localhost:8180/realms/ispf`
- Roles from JWT: `realm_access.roles` → `ROLE_admin`, `ROLE_operator`

### test

- RBAC disabled (`rbac-enabled: false`)
- All endpoints available without authorization.

## Access matrix

Rules: `IspfAuthorizationRules.java`.

| Endpoint | admin | developer | operator | public |
|----------|:-----:|:---------:|:--------:|:------:|
| `GET /api/v1/info` | ✓ | ✓ | ✓ | ✓ |
| `POST /api/v1/auth/login` | | | | ✓ |
| `GET /api/v1/auth/me` | ✓ | ✓ | ✓ | |
| `GET /actuator/health` | ✓ | ✓ | ✓ | ✓ |
| `WS /ws/**` | ✓ | ✓ | ✓ | ✓ |
| `GET /api/v1/**` | ✓ | ✓ | ✓ | |
| `POST /api/v1/events/**` | ✓ | ✓ | ✓ | |
| `POST .../functions/invoke` | ✓ | ✓ | ✓ | |
| `POST /api/v1/bff/**` | ✓ | ✓ | ✓ | |
| `POST /api/v1/workflows/instances/*/cancel` | ✓ | ✓ | ✓ | |
| `GET/POST /api/v1/work-queue/**` | ✓ | ✓ | ✓ | |
| `POST/PUT/PATCH/DELETE` solution config (`/objects`, `/applications`, data-sources, …) | ✓ | ✓ | | |
| `PUT /api/v1/objects/by-path/acl` | ✓ | | | |
| `/api/v1/platform/metrics`, `/runtime-settings`, `/update/**`, … | ✓ | | | |
| `/api/v1/security/**`, `/federation/**`, `/tenants/**` | ✓ | | | |
| `/api/v1/ai/**` (except `GET /ai/provider`) | ✓ | ✓ | | |
| `/api/v1/applications/**` deploy | ✓ | ✓ | | |
| `/api/v1/schedules/**` | ✓ | ✓ | | |
| `/api/v1/alert-rules/**`, `/correlators/**` | ✓ | ✓ | | |
| `/api/v1/blueprints/**` (write) | ✓ | ✓ | | |

**operator** can: read objects/dashboards/workflows, invoke functions (except script-only like `executeQuery`), work queue, fire events, write writable variables.

**developer** can: all solution configuration (objects, applications, platform SQL tools, `executeQuery`, **AI Studio**), but **not** system settings, security, federation, tenants, license/cluster.

**admin** can: everything above + platform administration.

## Keycloak (development)

Docker Compose starts Keycloak on port **8180**.

Realm `ispf` setup:

1. Admin console: http://localhost:8180 — user `admin` / `admin`
2. Create realm **ispf**.
3. Create client (public or confidential) for web/API.
4. Realm roles: `admin`, `operator`
5. Assign roles to users
6. Start server: `--spring.profiles.active=dev`

Web Console (`dev` profile): **Sign in with Keycloak** (OIDC PKCE, client `ispf-web-console`). Realm imported from `deploy/keycloak/ispf-realm.json` on `docker compose up`.

### Per-object ACL

| Endpoint | Description |
|----------|----------|
| `GET /api/v1/objects/by-path/acl?path=` | List object ACL rules |
| `PUT /api/v1/objects/by-path/acl?path=` | Replace ACL rules |

Rules: `principalType` (`ROLE`/`USER`), `principalId`, `permission` (`READ`/`WRITE`/`INVOKE`). If no rules on object or ancestor — use global RBAC. `admin` always has full access.

Web Console: **Access** tab in object inspector (admin).

## Variables

| Variable | Description |
|------------|----------|
| `ISPF_OAUTH_ISSUER` | JWT issuer URI |
| `ispf.security.rbac-enabled` | Enable/disable RBAC |
| `ispf.security.token-auth-enabled` | Bearer sessions (local) |
| `ispf.security.local-default-role` | Default role without token (local, dev only) |
| `ispf.security.mfa.enabled` | Enable TOTP enrollment API (`/api/v1/security/mfa/**`) |

## MFA (TOTP foundation)

Configuration: `ispf.security.mfa.enabled` (default `false`, env `ISPF_MFA_ENABLED`).

When MFA is enabled, authenticated users (any role with read API access) can:

| Endpoint | Description |
|----------|----------|
| `GET /api/v1/security/mfa/status` | MFA status and pending enrollment |
| `POST /api/v1/security/mfa/enroll` | Start TOTP enrollment (secret + `otpauth://` URI) |
| `POST /api/v1/security/mfa/verify` | Confirm 6-digit code (stub — full TOTP verification in next sprint) |
| `DELETE /api/v1/security/mfa/enroll` | Cancel pending enrollment |

Implementation — `MfaService` skeleton (in-memory pending state). Production: Keycloak OTP/WebAuthn (BL-153) or persistent storage in ISPF.

## Per-variable ACL

Variables can define `readRoles` / `writeRoles` (JSON array of role names). Empty list = inherit object ACL.

Checked in `ObjectAccessService.requireVariableRead/Write` on read/write/history. Web Console: badges and ACL editor in variable dialog.

## Production recommendations

- Prefer `--spring.profiles.active=prod` (see `application-prod.yml`) or set the env vars below
- Do not expose `local` profile on internet-facing hosts
- Keycloak or another IdP with shortest practical JWT TTL
- TLS at ingress
- Restrict `permitAll` endpoints
- Secrets via vault, not in config files
- Set `ISPF_LICENSE_ENFORCE=true` and `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true`
- Set `ISPF_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` to your console origin(s) (default is localhost-only; `local`/`test` keep `*`)
- Keep `ispf.security.local-role-header-enabled=false`

Default users (`admin`/`admin`, …) are intentional for **local / test / lab** only — not a production defect.

`StartupSecurityGuard` logs warnings at startup when license enforce, signed bundles, WS origins, or RBAC look unsafe outside `local`/`test`.
