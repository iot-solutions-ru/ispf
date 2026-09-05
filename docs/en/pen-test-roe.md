# Pen-test rules of engagement (G-01 deeper)

> **Status:** Operator + assessor addendum — attach to SOW with [pen-test-scope.md](pen-test-scope.md).  
> **Not** a passed assessment. Prep hub: [pen-test-prep.md](pen-test-prep.md).

This page expands the short RoE bullets in the scope doc into a kickoff-ready checklist.

---

## 1. Authorization package (must exist before probes)

| Artifact | Owner | Notes |
|----------|-------|-------|
| Signed SOW citing scope + this RoE | Legal / security owner | |
| Written allow-list (FQDN, IP, CIDR, ports) | Infra | Include jump host if used |
| Test window (UTC) + timezone of lab | Both | Soft vs hard stop times |
| Emergency stop contact (phone + chat) | Operator | 24h reachable during window |
| Build freeze pin | Operator | Preflight JSON + image digest |

No verbal-only authorization. Shared demostand probes still need written allow-list + RPS cap.

---

## 2. Classification of activity

| Class | Allowed where | Examples (intent only) |
|-------|---------------|------------------------|
| **Recon (read-only)** | Demostand + lab | Public `/api/v1/info`, `/actuator/health`, `/api/v1/auth/config`, OpenAPI if published |
| **AuthZ / IDOR** | Demostand (low RPS) + lab | Role/tenant isolation checks from case catalog |
| **Grey-box authenticated** | Lab / staging | Admin + tenant fixtures; MFA enrolment for grey-box |
| **Destructive / fuzz** | Disposable staging only | Upload bombs, mass create/delete, schema-breaking payloads |
| **Load / DoS** | Never on shared demostand; staging only with written addendum | |

Assessor tools are their choice; ISPF does **not** ship attack scripts. Case intents live in [pen-test-cases.md](pen-test-cases.md).

---

## 3. Rate limits & collateral

- Honor HTTP `429`, WAF blocks, and operator chat “slow down”.
- Default shared demostand cap unless SOW says otherwise: **≤ 2 req/s** sustained, burst ≤ 10.
- No concurrent credential stuffing against shared IdP.
- No scanning of hosts outside allow-list (neighbor VMs, GPU/vLLM, management planes).

---

## 4. Data handling & retention

| Rule | Detail |
|------|--------|
| Minimal capture | Screenshots/HAR only as needed for a finding |
| No bulk exfil | Do not download full historian dumps “for later” |
| Secrets | Lab passwords / TOTP seeds via vault or encrypted channel; wipe assessor password managers after close |
| PII | Redact user emails/phones in report appendices if not material |
| Retention | Assessor keeps raw notes ≤ **90 days** after final report unless contract says longer; operator archives redacted pack in `docs/evidence/security-pentest/` |
| Git | Never commit live credentials, raw HAR with tokens, or unredacted customer data |

---

## 5. Severity rubric (ISPF-oriented)

Map findings to CVSS **and** the business labels below so remediation owners agree on SLA.

| Label | Typical CVSS | ISPF examples | Notify |
|-------|--------------|---------------|--------|
| **Critical** | ≥ 9.0 | Auth bypass; cross-tenant write; RCE via plugin path | **24h** |
| **High** | 7.0–8.9 | Cross-tenant read; privilege escalation admin←operator; MFA bypass when MFA required | **5 business days** |
| **Medium** | 4.0–6.9 | Stored XSS in admin UI; IDOR on non-sensitive metadata; weak session hygiene | Next sprint plan |
| **Low** | 0.1–3.9 | Missing secondary headers; verbose 500 without secret leak | Backlog |
| **Info** | — | Defense-in-depth suggestions; demostand lab-default password (document accepted risk) | Note in report |

Accepted risks (e.g. documented lab defaults on non-prod demostand) must appear explicitly in the report — not silently omitted.

---

## 6. Disclosure & CVE

1. Assessor → operator private channel first (Critical 24h / High 5bd).  
2. Public advisory / CVE only after patch or documented mitigation is available **or** mutually agreed delay.  
3. Scorecard / tender language stays **Gap (G-01)** until report + retest letter exist.

---

## 7. Stop conditions (immediate halt)

- Suspected impact on safety / control loops / physical plant  
- Ransomware-class, wiper, or indiscriminate encryption behavior  
- Shared-tenant collateral (other customers on same host)  
- Operator “STOP” message or allow-list revocation  

After halt: preserve logs, do not “finish the PoC”, wait for written resume.

---

## 8. Kickoff agenda (≈ 60–90 min)

1. Introductions + emergency contacts (5 min)  
2. Scope lock: in / out / addenda (15 min)  
3. Environment map: demostand vs lab vs staging (10 min)  
4. Account handoff procedure (vault links, MFA) (10 min)  
5. Version freeze: run or review `pen-test-preflight.sh` output (10 min)  
6. Case catalog walkthrough — mark N/A early (15 min)  
7. Reporting format + severity rubric + SLAs (10 min)  
8. Windows, RPS, stop conditions confirmation (5 min)  

Questionnaire for firm selection: [pen-test-questionnaire.md](pen-test-questionnaire.md).

---

## 9. Related

- [pen-test-scope.md](pen-test-scope.md) · [pen-test-prep.md](pen-test-prep.md) · [pen-test-cases.md](pen-test-cases.md)  
- [pen-test-questionnaire.md](pen-test-questionnaire.md)  
- Evidence templates under `docs/evidence/security-pentest/`

RU: see [pen-test-prep.md](../ru/pen-test-prep.md) (this RoE stays EN-canonical for SOW attachment).
