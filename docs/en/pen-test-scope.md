# Independent pen-test scope (G-01 prep)

> **Status:** Prep only — **does not** claim a passed assessment.  
> Gap register: [compliance-tender-pack.md](compliance-tender-pack.md) **G-01**.  
> Hub: [doc-status.md](doc-status.md).

This page is the **rules-of-engagement draft** an integrator or hired firm can attach to an SOW. Shipping this doc closes the *prep* slice of G-01; closing G-01 itself still requires a dated third-party report.

---

## Objective

Demonstrate that ISPF (server + web console + typical OT/IT surfaces) withstands a time-boxed black/grey-box assessment without critical/high unmitigated findings on the in-scope build, **or** that findings have agreed remediation owners and dates.

**Out of objective:** IEC 62443 product certification, GAMP CSV package, red-team of customer OT plants.

---

## In scope (default SOW)

| Surface | Notes |
|---------|--------|
| ISPF HTTP/JSON API (`/api/v1/**`) | Authn/authz, MFA TOTP, tenant isolation, audit export |
| Web Console (SPA) | XSS/CSRF class issues, session/token handling, role UX bypass attempts |
| WebSocket / Object tree push | Auth handshake, subscription ACL |
| Historian read APIs + ClickHouse-backed paths when enabled | IDOR across tenants/objects; no destructive load tests without approval |
| OIDC / local login | Token confusion, privilege escalation, MFA bypass |
| Driver/plugin pack **upload** & signing gates (if enabled) | Malicious pack rejection |
| Multi-tenant logical isolation | Tenant A must not read Tenant B objects/history when RLS/logical mode on |

Suggested **reference environments** (operator-provided; credentials out of band):

| Env | Role |
|-----|------|
| Public demostand (read-mostly) | Smoke / low-impact probes |
| Private lab (e.g. LAN behind jump) | Grey-box with admin + tenant-admin accounts |
| Disposable staging clone | Destructive / fuzz allowed |

---

## Explicitly out of scope (unless SOW addendum)

- Production customer plants, PLCs, safety systems, radio/OT physical access
- DoS / volumetric floods against shared demostand
- Social engineering of ISPF staff or customer operators
- Supply-chain of upstream OS/JDK beyond documented SBOM review
- Source-code escrow / full white-box of all modules (optional paid add-on)

---

## Rules of engagement

1. **Written authorization** before any probe (this page + signed SOW + env allow-list).
2. **Rate limits:** respect `429` / WAF; no more than agreed RPS on shared demostand.
3. **Data handling:** no exfiltration of PII beyond PoC screenshots in the report; wipe lab tokens after engagement.
4. **Disclosure:** 24h for Critical, 5 business days for High; coordinate public CVE only after patch availability.
5. **Stop conditions:** suspected safety impact, ransomware-class behavior, or shared-tenant collateral → halt and call operator contact.

**Deeper RoE** (severity rubric, retention, kickoff agenda, activity classes): [pen-test-roe.md](pen-test-roe.md).

---

## Suggested methodology

| Phase | Duration (indicative) | Activities |
|-------|----------------------|------------|
| 0 Kickoff | 0.5 d | Scope lock, accounts, build SHA / version pin |
| 1 Recon | 1 d | OpenAPI map, role matrix, tenant fixtures |
| 2 Authn/Z | 2–3 d | Login/MFA, JWT/opaque tokens, RBAC, IDOR |
| 3 App & WS | 2 d | Console XSS, CSRF, WS ACL |
| 4 Tenancy / historian | 1–2 d | Cross-tenant reads, history export |
| 5 Report | 1–2 d | CVSS-tagged findings + retest plan |

Tools: assessor choice (Burp/ZAP/custom). ISPF provides OpenAPI + [security](security.md) + this scope.

---

## Deliverables to close G-01

| Artifact | Required |
|----------|----------|
| PDF/Markdown report with scope, version, dates, CVSS findings | Yes |
| Retest letter or appendix after remediations | Yes for “pass” claim |
| Evidence folder under `docs/evidence/security-pentest/` (redacted) | Recommended for scorecard |

Until those exist, scorecard / tender language must say **Gap (G-01)** — not “pentest passed”.

---

## Deeper prep

| Doc | Role |
|-----|------|
| [pen-test-prep.md](pen-test-prep.md) | Operator freeze / accounts / kickoff checklist |
| [pen-test-roe.md](pen-test-roe.md) | Full RoE + severity + stop conditions |
| [pen-test-questionnaire.md](pen-test-questionnaire.md) | Vendor / RFP questionnaire |
| [pen-test-cases.md](pen-test-cases.md) | Assessor case catalog |
| Preflight | `bash tools/security/pen-test-preflight.sh https://HOST --out preflight.json` |

## Related

- [compliance-tender-pack.md](compliance-tender-pack.md) — gap register  
- [security.md](security.md) — MFA, ACL, hardening flags  
- [multi-tenant.md](multi-tenant.md) — isolation modes  
- [parked-backlog.md](parked-backlog.md) — **P-PENTEST**  
- ADR [0056 — WebAuthn / IdP MFA](decisions/0056-webauthn-idp-mfa.md) — MFA follow-up (BL-194)

RU: [pen-test-scope.md](../ru/pen-test-scope.md) · [pen-test-prep.md](../ru/pen-test-prep.md)
