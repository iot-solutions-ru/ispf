# Pen-test vendor questionnaire (G-01)

> Use when selecting or briefing a hired firm. Pair with [pen-test-scope.md](pen-test-scope.md) and [pen-test-roe.md](pen-test-roe.md).  
> Answers stay in the commercial SOW / vault — **do not** paste secrets into git.

---

## A — Firm & methodology

| # | Question | Answer |
|---|----------|--------|
| A1 | Legal entity, jurisdiction, insurance (cyber / E&O) | |
| A2 | Lead assessor name + years OT/ICS or industrial SaaS experience | |
| A3 | Proposed methodology (OWASP ASVS / PTES / custom) and mapping to our case catalog | |
| A4 | Black-box / grey-box / white-box mix proposed | |
| A5 | Sample redacted report (structure only) available? | |
| A6 | Subcontractors allowed? If yes, named in SOW? | |

---

## B — Scope fit for ISPF

| # | Question | Answer |
|---|----------|--------|
| B1 | Confirm in-scope: HTTP API, Web Console SPA, WebSocket, historian read, OIDC/local+MFA, plugin upload gates | |
| B2 | Confirm out-of-scope: customer OT plants, volumetric DoS on demostand, social engineering | |
| B3 | Experience with multi-tenant SaaS isolation testing | |
| B4 | Experience with ClickHouse / time-series IDOR class tests | |
| B5 | Willing to mark cases Pass/Fail/N/A against [pen-test-cases.md](pen-test-cases.md) | |

---

## C — Logistics

| # | Question | Answer |
|---|----------|--------|
| C1 | Preferred engagement length (calendar days) | |
| C2 | On-VPN / jump-host capability | |
| C3 | Working hours overlap with operator TZ | |
| C4 | Critical finding notification channel + 24h commitment | |
| C5 | Language of final report (EN required for scorecard; RU optional) | |

---

## D — Deliverables & retest

| # | Question | Answer |
|---|----------|--------|
| D1 | Report includes CVSS 3.1, CWE, affected version pin? | |
| D2 | Retest included for Critical/High within N days? Fee? | |
| D3 | Accept redacted archive under operator `docs/evidence/security-pentest/` | |
| D4 | Raw notes retention ≤ 90 days (or contract alternate) | |
| D5 | CVE coordination process if public disclosure needed | |

---

## E — Commercial (operator fills privately)

| # | Question | Answer |
|---|----------|--------|
| E1 | Fixed price vs T&M | |
| E2 | Travel / VPN appliance costs | |
| E3 | NDA / DPA signed date | |

---

## Scoring hint (internal)

Prefer firms that: (1) accept our RoE/RPS caps, (2) map to the case catalog, (3) include retest for High+, (4) OT/multi-tenant experience. Lowest bid alone is not a selection criterion for G-01.
