# Vendor engagement pack — ISPF pen-test (G-01)

> **Status:** Vendor-ready **prep** — attach to RFP / SOW.  
> **Does not** claim a passed assessment. Closing G-01 still requires a dated third-party report + retest letter.  
> Gap: [compliance-tender-pack.md](compliance-tender-pack.md) **G-01** · Parked: [parked-backlog.md](parked-backlog.md) **P-PENTEST**.

This page is the **single pack to send a hired firm today**. Methodology details live in linked docs; secrets stay in the operator vault.

---

## 1. What to attach (ordered)

| # | Artifact | Path |
|---|----------|------|
| 1 | This pack (cover) | [pen-test-vendor-pack.md](pen-test-vendor-pack.md) |
| 2 | Scope / SOW draft | [pen-test-scope.md](pen-test-scope.md) |
| 3 | Rules of engagement | [pen-test-roe.md](pen-test-roe.md) |
| 4 | Vendor questionnaire | [pen-test-questionnaire.md](pen-test-questionnaire.md) |
| 5 | Case catalog | [pen-test-cases.md](pen-test-cases.md) |
| 6 | Operator prep / kickoff | [pen-test-prep.md](pen-test-prep.md) |
| 7 | Lab inventory (no secrets) | [`../evidence/security-pentest/inventory-2026-09-08-lab.md`](../evidence/security-pentest/inventory-2026-09-08-lab.md) |
| 8 | Lab preflight (2026-09-09) | [`../evidence/security-pentest/2026-09-09-lab-192.168.100.10-preflight.md`](../evidence/security-pentest/2026-09-09-lab-192.168.100.10-preflight.md) |
| 9 | Findings template | [`../evidence/security-pentest/findings.template.md`](../evidence/security-pentest/findings.template.md) |
| 10 | Case-results matrix | [`../evidence/security-pentest/case-results.template.md`](../evidence/security-pentest/case-results.template.md) |
| 11 | Evidence index template | [`../evidence/security-pentest/evidence-index.template.md`](../evidence/security-pentest/evidence-index.template.md) |
| 12 | Security overview | [security.md](security.md) · [multi-tenant.md](multi-tenant.md) |

Commercial NDA/DPA + signed SOW are **operator legal** — not in git.

---

## 2. Proposed engagement (indicative)

| Phase | Duration | Focus |
|-------|----------|--------|
| 0 Kickoff | 0.5 d | Scope lock, accounts, version freeze |
| 1 Recon | 1 d | Surface map, role matrix (OpenAPI **not** published on lab) |
| 2 Authn/Z | 2–3 d | Local login, MFA policy as configured, RBAC, IDOR |
| 3 App & WS | 2 d | Web Console, WebSocket ACL |
| 4 Tenancy / historian | 1–2 d | Cross-tenant only if fixtures provided; historian read IDOR |
| 5 Report | 1–2 d | CVSS findings + retest plan |

**Default mix:** grey-box on private lab + limited black-box recon. White-box source review = paid add-on (see scope).

**Deliverables to close G-01:** full report (PDF/MD) + remediations owners/dates + **retest letter** for Critical/High. Archive redacted pack under `docs/evidence/security-pentest/YYYY-MM-DD-*/`.

---

## 3. Environment snapshot (lab — filled)

Pinned **2026-09-09** (refresh at kickoff if build changed).

| Field | Value |
|-------|--------|
| Base URL | `http://192.168.100.10:8080` |
| Reachability | Jump `84.42.21.226:5031` user `iot-solutions` → lab `iot-solutions@192.168.100.10` (SSH keys / passwords = **vault only**) |
| ISPF version | **0.9.207** · env `lab-enterprise-l` · profile `unified` · `clusterEnabled=false` |
| Image id (container) | `sha256:e0c18e11…1a3288` — re-record RepoDigest / jar SHA at freeze |
| Auth | **local** (`localLoginEnabled=true`) |
| MFA | **disabled** (`mfaEnabled=false`, `mfaRequiredForAdmin=false`) — document accepted risk or enable before window |
| Tenancy | **Single-tenant lab** — multi-tenant case family **N/A** until operator provisions tenant A/B fixtures |
| OpenAPI / Swagger | **Not exposed** (404) — hand over export OOB if assessor needs it |
| Security headers (sample) | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`; no HSTS (HTTP lab) |
| Allow-list | Lab OT VLAN `192.168.100.0/24` via jump only |
| Historian | Lab Enterprise L stack (Postgres + ClickHouse present) |

Preflight JSON: [`2026-09-09-lab-192.168.100.10-preflight.json`](../evidence/security-pentest/2026-09-09-lab-192.168.100.10-preflight.json).

### Explicit non-targets (lab)

- Destructive delete / DoS on shared lab DBs while **Pilot #1–#3** soak timers run (06:00 / 06:05 / 06:10 MSK)
- OT peers / Mosquitto / OPC UA loopback used by BL-140 pilots unless assessor + OT lead agree in writing
- Public internet targets without a separate inventory + RoE

---

## 4. Assessor inputs

**We provide (in-repo / this pack):** scope, RoE, case catalog, findings + case-results templates, lab inventory/preflight (no secrets), security docs.

**Operator provides out-of-band (vault):**

| Item | Notes |
|------|--------|
| Written authorization email | Hosts, ports, UTC window, RPS / WAF |
| Accounts | admin / operator / viewer (+ tenant A/B only if isolation in scope) |
| MFA seed | Only if MFA enabled for window |
| OpenAPI export | If not published on target |
| Contacts | Security owner, infra on-call, stop-condition phone |
| Engagement dates / SOW id | Fill at contract |

---

## 5. Operator kickoff checklist (human)

- [ ] Firm selected (answers to [pen-test-questionnaire.md](pen-test-questionnaire.md))
- [ ] NDA/DPA + SOW signed citing scope + RoE
- [ ] Written auth email issued
- [ ] Accounts issued from vault; MFA posture decided
- [ ] Image digest / jar SHA frozen + new preflight if build changed
- [ ] OT soak owners notified (lab)
- [ ] Kickoff agenda ([pen-test-roe.md](pen-test-roe.md) §8)
- [ ] After engagement: redacted report + retest → then update G-01 / P-PENTEST / scorecard

Until the report + retest exist: language stays **Gap (G-01)** — not “pentest passed”.

---

## 6. Questionnaire reply (firm fills)

Copy tables from [pen-test-questionnaire.md](pen-test-questionnaire.md) sections **A–E** into the commercial SOW. Do **not** commit filled commercial answers with pricing/secrets into git.

---

## 7. Honesty

| Claim | Allowed? |
|-------|----------|
| “Vendor pack / SOW prep ready” | **Yes** (this page) |
| “Internal engineering review done” | **Yes** — [2026-09-09-engineering-review](../evidence/security-pentest/2026-09-09-engineering-review/) (defensive only) |
| “Independent pen-test passed” | **No** until dated report + retest under `docs/evidence/security-pentest/` |
| Scorecard Security ≥9 / G-01 closed | **No** on pack alone (current code-verified Security **8.5 PARTIAL**) |

RU pointer: [pen-test-vendor-pack.md](../ru/pen-test-vendor-pack.md)
