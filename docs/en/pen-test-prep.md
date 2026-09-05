# Pen-test operator prep (G-01 deeper)

> **Status:** Prep runbook — **not** a passed assessment.  
> Legal / SOW surface: [pen-test-scope.md](pen-test-scope.md).  
> Case catalog: [pen-test-cases.md](pen-test-cases.md).  
> Gap: [compliance-tender-pack.md](compliance-tender-pack.md) **G-01**.

Use this page **before** a hired firm starts. Goal: freeze version, accounts, allow-list, and evidence paths so findings map to a reproducible build.

---

## 1. Freeze the target

| Item | How | Store in |
|------|-----|----------|
| Platform version | `GET /api/v1/info` → `version` | inventory sheet |
| Git / image digest | deploy tag or `docker inspect` digest | inventory sheet |
| Build time / env | `environment` from `/api/v1/info` | inventory sheet |
| Auth mode | `GET /api/v1/auth/config` → `local` / `oidc` | inventory sheet |
| MFA flags | `ISPF_MFA_ENABLED`, `ISPF_MFA_REQUIRED_FOR_ADMIN` | inventory sheet |
| Tenancy mode | see [multi-tenant.md](multi-tenant.md) | inventory sheet |
| Historian store | JDBC vs ClickHouse flags | inventory sheet |

Preflight script (lab/demostand, read-only):

```bash
bash tools/security/pen-test-preflight.sh https://TARGET_HOST
```

Writes a JSON stub under `/tmp` (or `--out`); copy redacted output into `docs/evidence/security-pentest/preflight-YYYY-MM-DD.json` **after** engagement kickoff (no passwords).

---

## 2. Accounts & roles (minimum set)

Provide **out of band** (never commit passwords):

| Account | Role intent | Used for |
|---------|-------------|----------|
| `admin` (or equivalent) | platform admin | ACL / MFA / user admin paths |
| `operator` | day-to-day operator | positive control + negative privilege tests |
| `viewer` / read-only | minimal role | IDOR / write denial |
| `tenant-a-admin` | tenant A admin | cross-tenant isolation |
| `tenant-b-admin` | tenant B admin | cross-tenant isolation |
| OIDC test user (if OIDC on) | IdP-backed login | token / claim tests |

Optional: one MFA-enrolled admin (TOTP seed shared via secure channel for grey-box only).

Template: [`docs/evidence/security-pentest/inventory.template.md`](../evidence/security-pentest/inventory.template.md).

---

## 3. Environment allow-list

| Env | Allowed activity | Forbidden |
|-----|------------------|-----------|
| Shared demostand | Low RPS recon, authz IDOR, read APIs | DoS, mass user create, destructive delete |
| Private lab (`192.168.100.10` class) | Grey-box, fuzz, plugin upload tests | Touching unrelated GPU/vLLM stacks without owner OK |
| Disposable staging | Full destructive suite | Promoting findings as “prod passed” |

Document jump host / VPN in the inventory sheet; keep credentials in operator vault.

---

## 4. Kickoff checklist (T-1 day)

- [ ] SOW signed with [pen-test-scope.md](pen-test-scope.md) attached  
- [ ] Written authorization email with allow-list hosts + windows  
- [ ] Version pin recorded (`/api/v1/info` + image digest)  
- [ ] Accounts issued + MFA policy stated  
- [ ] OpenAPI / Swagger link or export handed over  
- [ ] Contacts: security owner, infra on-call, stop-condition phone  
- [ ] Rate-limit / WAF expectations agreed  
- [ ] Findings template shared: [`findings.template.md`](../evidence/security-pentest/findings.template.md)  
- [ ] Case list acknowledged: [pen-test-cases.md](pen-test-cases.md)  

---

## 5. During engagement

1. Assessor works the [case catalog](pen-test-cases.md); mark Pass / Fail / N/A / Skip.  
2. Critical → notify within **24h** (scope RoE).  
3. Do not patch silently mid-test without noting build change (breaks freeze).  
4. If build must change: new preflight JSON + note in findings log.

---

## 6. After engagement (to close G-01)

| Step | Artifact |
|------|----------|
| 1 | Full report (PDF/MD) with CVSS |
| 2 | Remediation tickets with owners/dates |
| 3 | Retest letter |
| 4 | Redacted pack under `docs/evidence/security-pentest/YYYY-MM-DD-*/` |
| 5 | Update G-01 row in compliance pack + scorecard honesty note |

Until steps 1–3 exist: language stays **Gap (G-01)**.

---

## 7. Related

- [pen-test-scope.md](pen-test-scope.md) · [pen-test-cases.md](pen-test-cases.md) · [security.md](security.md)  
- [parked-backlog.md](parked-backlog.md) **P-PENTEST**  
- ADR [0056](decisions/0056-webauthn-idp-mfa.md) (MFA follow-up; not a substitute for pen-test)

RU: [pen-test-prep.md](../ru/pen-test-prep.md)
