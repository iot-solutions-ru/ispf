# Parked backlog board (Post-S33)

> **Policy:** [roadmap — quality over features](roadmap.md#execution-policy--quality-over-features).  
> Parked items stay parked until a **named** field / integration / customer task. This board is the honest status — not a schedule.

| ID | Item | Status | Unpark criteria | Latest note |
|----|------|--------|-----------------|-------------|
| P-ENT-L | Enterprise L (50k history tags + 1B CH + multi-tag SLO) | **Lab PASS** (2026-09-05) | Scorecard Historian rescore on full audit | Evidence: [`docs/evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md). Synthetic CH fill; tooling PR for `history-enabled-count` may still be merging. |
| P-HMI-8H | HMI offline stretch **8 h** (lab CDP) | **In progress** | Dated JSON + journal under `docs/evidence/hmi-offline/` | Lab CDP (`pwa:offline-field-soak`, 480 min) started 2026-09-05 against demostand. **Not** on-site tablet airplane mode. |
| P-HMI-FIELD | On-site tablet / airplane-mode soak (2h min / 8h stretch) | **Parked** | Named site + tablet + journal | Playbook: [hmi-offline-field-soak.md](hmi-offline-field-soak.md). |
| P-OT | Phase 25 OT Trust field pilots (BL-140…) | **Parked** | Named driver + plant task | Wave 1 checklist remains under roadmap; BL-191 matrix honesty Done. |
| P-ERP | Live ERP connector (BL-169) | **Parked** | Named 1C/SAP integration | Sandbox / simulate catalog may exist; **live** round-trip is not claimed. |
| P-BPMN | BPMN/DMN depth beyond ADR-0047 | **Parked** | Named customer blocker + ADR | Freeze holds. |
| P-WEBAUTHN | WebAuthn / passkeys (BL-194) | **Parked (Planned)** | Tender / customer MFA requirement | TOTP MFA GA remains the shipped path. |
| P-PENTEST | Third-party pen-test report (G-01) | **Parked** (prep deepened) | Hired assessment + dated report + retest | SOW [pen-test-scope.md](pen-test-scope.md); operator [pen-test-prep.md](pen-test-prep.md); cases [pen-test-cases.md](pen-test-cases.md); preflight `tools/security/pen-test-preflight.sh`. Docs ≠ cert. |

## Lab access (operators)

Private lab used for Enterprise L:

| Role | Address | Notes |
|------|---------|-------|
| Jump | `84.42.21.226:5031` | SSH user provided out-of-band |
| ISPF/DB node | `192.168.100.10` | Passwordless SSH **from jump**; Postgres `:5432`, ClickHouse `:8123`, ISPF `:8080` when lab stack is up |

**Do not commit passwords or tokens.** Rotate if exposed in chat logs.

## What “done” means here

- **Lab PASS** closes the *measurement* gap for scorecard inputs; frozen scores still wait for the next full competitive audit.
- **Parked** means no active delivery plan — only resume with a named task.
- **In progress** means automation is running; attach evidence when the run finishes.

RU: [parked-backlog.md](../ru/parked-backlog.md)
