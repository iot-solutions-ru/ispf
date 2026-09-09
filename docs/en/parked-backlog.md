# Parked backlog board (Post-S33)

> **Policy:** [roadmap — quality over features](roadmap.md#execution-policy--quality-over-features).  
> Parked items stay parked until a **named** field / integration / customer task. This board is the honest status — not a schedule.

| ID | Item | Status | Unpark criteria | Latest note |
|----|------|--------|-----------------|-------------|
| P-ENT-L | Enterprise L (50k history tags + 1B CH + multi-tag SLO) | **Consumed** (scorecard 2026-09-09) | — | Folded into Historian **9.5** on [competitive-scorecard](competitive-scorecard.md). Evidence: [`historian-scale/2026-09-05-…`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md). Synthetic CH fill noted in scorecard honesty. |
| P-HMI-8H | HMI offline stretch **8 h** (lab CDP) | **Consumed** (scorecard 2026-09-09) | — | Folded into HMI **8.0** (with FPS evidence). CDP only — **P-HMI-FIELD** still required for 9+. Evidence: [`hmi-offline/2026-09-05-…`](../evidence/hmi-offline/2026-09-05-ispf-vps-0.9.207-offline-8h.json). |
| P-HMI-FIELD | On-site tablet / airplane-mode soak (2h min / 8h stretch) | **Parked** | Named site + tablet + journal | Playbook: [hmi-offline-field-soak.md](hmi-offline-field-soak.md). |
| P-OT | Phase 25 OT Trust field pilots (BL-140…) | **In progress** (P1 days 1–4; P2 MQTT day 2; P3 OPC UA day 2; timers armed) | 7-day soak + OT sign-off per pilot | [P1 day4](../evidence/ot-trust/2026-09-09-bl140-pilot1-day4.md) · [P2 day2](../evidence/ot-trust/2026-09-09-bl140-pilot2-mqtt-day2.md) · [P3 day2](../evidence/ot-trust/2026-09-09-bl140-pilot3-opcua-day2.md). Timers 06:00/05/10 MSK. Not OT 10/10. |
| P-ERP | Live ERP connector (BL-169) | **Parked** | Named 1C/SAP integration | Sandbox / simulate catalog may exist; **live** round-trip is not claimed. |
| P-BPMN | BPMN/DMN depth beyond ADR-0047 | **Parked** | Named customer blocker + ADR | Freeze holds. |
| P-WEBAUTHN | WebAuthn / passkeys (BL-194) | **Parked (Planned)** | Tender / customer MFA requirement | TOTP MFA GA remains the shipped path. |
| P-PENTEST | Third-party pen-test report (G-01) | **Parked** (vendor-pack ready) | Hired assessment + dated report + retest | **Send:** [pen-test-vendor-pack.md](pen-test-vendor-pack.md). SOW [pen-test-scope.md](pen-test-scope.md); RoE [pen-test-roe.md](pen-test-roe.md); RFP [pen-test-questionnaire.md](pen-test-questionnaire.md); prep [pen-test-prep.md](pen-test-prep.md); cases [pen-test-cases.md](pen-test-cases.md); lab pin [2026-09-09 preflight](../evidence/security-pentest/2026-09-09-lab-192.168.100.10-preflight.md). Docs ≠ cert. |

## Lab access (operators)

Private lab used for Enterprise L:

| Role | Address | Notes |
|------|---------|-------|
| Jump | `84.42.21.226:5031` | SSH user provided out-of-band |
| ISPF/DB node | `192.168.100.10` | Passwordless SSH **from jump**; Postgres `:5432`, ClickHouse `:8123`, ISPF `:8080` when lab stack is up |

**Do not commit passwords or tokens.** Rotate if exposed in chat logs.

## What “done” means here

- **Lab PASS** closes the *measurement* gap for scorecard inputs; scores move only on a **named full competitive audit** ([competitive-scorecard](competitive-scorecard.md) — last: 2026-09-09 → ~8.0/10).
- **Consumed** means that Lab PASS was folded into the scorecard; residual field gaps stay on other parked IDs (e.g. **P-HMI-FIELD**).
- **Parked** means no active delivery plan — only resume with a named task.
- **In progress** means automation is running; attach evidence when the run finishes.

RU: [parked-backlog.md](../ru/parked-backlog.md)
