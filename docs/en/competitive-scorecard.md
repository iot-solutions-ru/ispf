# ISPF competitive scorecard (BL-189)

> **Status:** Stable — Code-verified readiness. Hub: [doc-status.md](doc-status.md).

Public readiness matrix for Phases 25–33 (see unified [roadmap](roadmap.md)).

**Version pin (do not conflate):**

| Pin | Meaning |
|-----|---------|
| **0.9.207** | **Full competitive audit 2026-09-09** — scores and evidence tables below. Lab evidence folded; field/OT/MES honesty preserved. |
| **0.9.102** | Prior full code audit (July 2026). Historical baseline; superseded by this audit. |
| **0.9.206** | Prior demostand pin (migration scope). **Not** a scored matrix. |

**Current column:** **Code verified** — evidence from `main` source, tests, and dated lab archives at audit pin **0.9.207** (2026-09-09).

Scale **1–10** vs leading platforms: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, mature context-tree IIoT platforms.

| # | Dimension | Baseline (Phase 24) | **Code verified (0.9.207)** | Target | Phase / BL |
|---|-----------|:-------------------:|:---------------------------:|:------:|------------|
| 1 | Unified data model (object tree) | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-25--ot-trust), [roadmap](roadmap.md#phase-29--mes-platform), [roadmap](roadmap.md#phase-30--automation-depth) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / mimics | 7.0 | **8.0** | **10** | [roadmap](roadmap.md#phase-26--hmi-excellence) — BL-146…152 |
| 3 | OT/IT connectivity (drivers.md) | 6.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-25--ot-trust) — BL-140…145; BL-191 honesty Done |
| 4 | Historian / time-series | 7.0 | **9.5** | **10** | [roadmap](roadmap.md#phase-28--historian-at-scale) — BL-159…163; [roadmap](roadmap.md#phase-33--analytics-platform-af-capable) BL-200…210 |
| 5 | Automation / alarms | 7.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-27--enterprise-security), [roadmap](roadmap.md#phase-30--automation-depth) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-30--automation-depth) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.5** | **10** | [roadmap](roadmap.md#phase-29--mes-platform) — BL-164…170 |
| 8 | Low-code velocity | 8.0 | **8.0** | **10** | [roadmap](roadmap.md#phase-26--hmi-excellence), [roadmap](roadmap.md#phase-31--ai-autopilot) — BL-146…152, BL-177…180 |
| 9 | AI-assisted development | 9.0 | **9.0** | **10** | [roadmap](roadmap.md#phase-31--ai-autopilot) — BL-177…182 |
| 10 | Security / RBAC / tenancy | 6.5 | **8.5** | **10** | [roadmap](roadmap.md#phase-27--enterprise-security) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **7.5** | **10** | [roadmap](roadmap.md#phase-25--ot-trust), [roadmap](roadmap.md#phase-28--historian-at-scale), [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-144…145, BL-186…187 |
| 12 | Ecosystem / marketplace | 4.0 | **6.5** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.5** | **10** | maintain — Spring Boot 4, React 19, NATS, ClickHouse option |

**Overall (code verified): ~8.0/10** — simple mean of the 14 dimensions above (sum 112.0 / 14).

### Audit 2026-09-09 — score deltas vs 0.9.102

| # | Dimension | 0.9.102 | **0.9.207** | Why |
|---|-----------|:-------:|:-----------:|-----|
| 2 | SCADA / HMI | 7.5 | **8.0** | Demostand live FPS + unmocked 500-el; lab CDP **2h + 8h** offline (**P-HMI-8H**). Not 9+: **P-HMI-FIELD** still parked. |
| 4 | Historian | 7.0 | **9.5** | Enterprise L **Lab PASS** 2026-09-05 (50k history-enabled + ≥1B CH + multi-tag p95≈49 ms). Honesty: synthetic CH fill; demostand remains a small site. |
| 10 | Security / RBAC | 8.0 | **8.5** | Fold trusted-channel/OQ residual + PostgreSQL RLS Done. Not 9+: **G-01** pen-test still open; WebAuthn parked. |
| 11 | Deploy / scale / edge | 7.0→7.5* | **7.5** | Fold Phase 32 Helm/ARM/MoM post-audit delta into top matrix (*evidence table already had 7.5). |
| 12 | Ecosystem / marketplace | 5.0→6.5* | **6.5** | Fold Marketplace GA + CI catalog gate (*evidence table already had 6.5). |
| 3 | OT/IT | 7.0 | **7.0** | Lab catalog + in-progress pilots — **not** field Done / not OT 10/10. |
| — | MES / AI / BPMN / others | — | unchanged | No new leadership-bar proof. |

**Prior post-audit notes (Jul–Aug 2026)** are absorbed into this matrix. Program wave 8 (~9.8) remains **retracted**.

---

## Definition of done (10/10 overall)

From [roadmap](roadmap.md) (Phases 25–33 / DoD). Status on **0.9.207**:

| Criterion | Status |
|-----------|--------|
| All **14 dimensions ≥9.5**, none ≤8 (BL-189) | **Not met** — code verified mean ~8.0; dims ≤8 remain (OT, alarms, BPMN, MES, deploy, ecosystem) |
| Agent regression **≥95% green** with live LLM (BL-178) | **Met** — full live suite `AGENT_LIVE_SUITE_MODE=full` via `run-live-suite.sh`: **52/52 @100%** (`build/agent-regression/live-suite-results.json`, ~2026-07-18/19). Nightly CI still **platform** mode. |
| Marketplace GA checklist complete (BL-183) | **Met** — browse/install/sign/version + honest partner multi-endpoint + CI catalog gate; Partner Portal external |
| Competitive scorecard published per release (BL-189) | **Met** — this document |

---

## Code audit evidence (0.9.207)

Evidence classes: **REAL** (runtime + tests / dated lab), **PARTIAL** (core works, known gaps), **STUB** (explicit placeholder in source).

| # | Dimension | Score | Assessment | Key evidence |
|---|-----------|:-----:|---------|--------------|
| 1 | Unified data model | 8.5 | **REAL** | Object tree, CEL, blueprints; `AlertRuleListener` → `AlertRuleService`; correlators in `EventCorrelatorService` |
| 2 | SCADA / HMI | 8.0 | **PARTIAL** | `ispf-pid-v1` `totalSymbols: 218`; `ScadaMimicEditor.tsx`; demostand FPS [`hmi-fps/2026-09-04-…ui-pump-station`](../evidence/hmi-fps/2026-09-04-ispf-vps-0.9.207-ui-pump-station.json); unmocked 500-el [`…hmi-stress-500`](../evidence/hmi-fps/2026-09-04-ispf-vps-0.9.207-hmi-stress-500.json) (~47 FPS median); lab offline **2h** + **8h** CDP [`hmi-offline/…`](../evidence/hmi-offline/). Residual: on-site tablet (**P-HMI-FIELD**) |
| 3 | OT/IT drivers | 7.0 | **PARTIAL** | Catalog closed lab Waves 1–11 (**162/162**); BL-191 honesty Done; TOP-20 fixtures **14/20**; BL-140 pilots **in progress** (P1 day4 / P2 day2 / P3 OPC UA loopback day2 — [ot-trust](../evidence/ot-trust/)). Lab VLAN / loopback ≠ customer plant. DNP3 `writePoint` still throws |
| 4 | Historian | 9.5 | **REAL (lab SLO)** | JVM gates PASS ([`historian-scale/2026-09-04-jvm-…`](../evidence/historian-scale/2026-09-04-jvm-analytics-scale-gate.md)); Enterprise L lab ([`2026-09-05-lab-192.168.100.10-enterprise-l.md`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md)): 50k history-enabled + ≥1B CH + multi-tag p95≈49 ms. **Honesty:** synthetic CH fill; demostand is not the L catalog |
| 5 | Automation / alarms | 7.5 | **PARTIAL** | Alert rules + correlators **REAL**; alarm shelving approval **persisted** (BL-158) |
| 6 | Workflow / BPMN | 7.5 | **REAL** | `WorkflowEngineSubProcessTest`, `WorkflowEngineMessageTest`; not full BPMN 2.0; **P-BPMN** parked |
| 7 | MES / ISA-95 | 6.5 | **PARTIAL** | Marketplace `mes-platform`; stub ERP marks **`simulated`** (not live 1C/SAP — **P-ERP** / BL-169 parked). No production field MES site |
| 8 | Low-code velocity | 8.0 | **REAL** | Dashboard builder, bundle deploy (`MarketplaceLocalBundleService.installLocalBundle`), spreadsheets |
| 9 | AI-assisted development | 9.0 | **REAL** | BL-178 **52/52 @100%**; BL-177/180 live smoke harness; soft re-soaks under [`docs/evidence/ai-generator/`](../evidence/ai-generator/). Residual for 10: optional multi-day plant journal — soft archives do **not** mint new scores |
| 10 | Security / RBAC | 8.5 | **PARTIAL** | TOTP MFA GA (BL-153); ACL + trusted-channel (BL-154); SaaS tenant-admin + PG RLS (BL-155); SIEM audit (BL-156). Residual: hired pen-test (**G-01** / **P-PENTEST**), optional hard schema routing, WebAuthn (BL-194) |
| 11 | Deploy / scale / edge | 7.5 | **PARTIAL** | Federation MoM (BL-188); Helm + ARM edge (BL-186/187); no CI load proof for cluster / 10+ peer scale |
| 12 | Ecosystem / marketplace | 6.5 | **PARTIAL** | Marketplace GA + CI catalog gate (BL-183); partner directory `source=db` (BL-184); Partner Portal sync still external |
| 13 | Documentation / DX | 8.5 | **REAL** | Docs + ADRs; stub honesty in code; evidence folders under `docs/evidence/` |
| 14 | Stack modernity | 9.5 | **REAL** | Spring Boot 4, React 19, ClickHouse option in `gradle.properties` / `application.yml` |

### Known code integrity issues (fix before raising OT score)

| Issue | Location |
|-------|----------|
| DNP3 poll-only (BETA) — `writePoint` not implemented | `Dnp3DeviceDriver.writePoint()` — maturity honest; write still open |
| Partner Portal still external | `PartnerProgramService` persists directory/enroll (`source=db`); portal sync out of repo |
| OT field pilots incomplete | [ot-trust](../evidence/ot-trust/) — lab soaks in progress; not customer plant sign-off |

---

## Gaps to target

Priority fixes that move **code verified** scores toward 10/10 (not marketing claims). Full domain audit: [roadmap.md § Domain gap audit](roadmap.md#domain-gap-audit--iot--scada--mes--erp-2026-07-09).

1. **OT drivers (7.0 → 9+):** matrix honesty **closed (BL-191)**; remaining gap is **field** pilot sign-offs (7-day + OT contact) + DNP3 write / full DA stacks after **named field driver task** (BL-140). Lab pilots ≠ OT 10/10.
2. **ERP L4 / MES (6.5 → 9+):** live 1C or SAP connector (**BL-169** / **P-ERP** parked); production MES sites. Lab marketplace Done; score stays until field site + live ERP.
3. **AI (9.0 → 10):** harness bar Done. Residual: optional multi-day plant journal (not oneshot re-runs).
4. **Ecosystem (6.5 → 9+):** Partner Portal sync + first external partner-hosted catalog.
5. **Historian (9.5 → 10):** lab L gate met; residual is non-synthetic / demostand-scale honesty and ops playbooks for real customer L sites.
6. **HMI (8.0 → 9+):** on-site tablet / airplane-mode soak (**P-HMI-FIELD**); unmocked ≥60 FPS at 500-el still open.
7. **Security (8.5 → 9+):** dated third-party pen-test + retest letter (**G-01**); optional hard schema table routing; WebAuthn if tender requires (BL-194). Prep/preflight under [`security-pentest/`](../evidence/security-pentest/) ≠ cert.
8. **Compliance:** [compliance-tender-pack](compliance-tender-pack.md) docs Done (BL-192); no product certification claim.

---

## Program wave history (artifacts shipped)

Historical **program** scores tracked BL delivery velocity; they are **not** competitive readiness. Retained for release archaeology.

<details>
<summary>Wave 1–8 program columns (superseded by code verified)</summary>

| # | Dimension | W1 | W2 | W3 | W4 | W5 | W6 | W7 | W8 (program) |
|---|-----------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:------------:|
| 1 | Unified data model | 9.2 | 9.3 | 9.4 | 9.5 | 9.6 | 9.7 | 9.8 | 9.9 |
| 2 | SCADA / HMI | 7.8 | 7.9 | 8.0 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 3 | OT/IT connectivity | 6.8 | 6.9 | 7.0 | 8.5 | 8.7 | 9.3 | 9.5 | 9.7 |
| 4 | Historian | 7.5 | 7.6 | 7.7 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 5 | Automation / alarms | 8.0 | 8.1 | 8.2 | 8.6 | 8.7 | 9.1 | 9.4 | 9.6 |
| 6 | Workflow / BPMN | 7.0 | 7.1 | 7.2 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 7 | MES / ISA-95 | 6.2 | 6.4 | 6.6 | 8.5 | 8.8 | 9.5 | 9.6 | 9.7 |
| 8 | Low-code velocity | 8.5 | 8.6 | 8.7 | 8.8 | 9.0 | 9.5 | 9.6 | 9.7 |
| 9 | AI-assisted development | 9.4 | 9.6 | 9.7 | 9.8 | 9.85 | 9.95 | 9.97 | 9.98 |
| 10 | Security / RBAC | 7.2 | 7.3 | 7.4 | 8.0 | 8.8 | 9.3 | 9.5 | 9.7 |
| 11 | Deploy / scale / edge | 8.3 | 8.6 | 9.0 | 9.2 | 9.3 | 9.5 | 9.6 | 9.7 |
| 12 | Ecosystem / marketplace | 5.0 | 5.5 | 6.0 | 8.5 | 8.7 | 9.0 | 9.3 | 9.6 |
| 13 | Documentation / DX | 9.3 | 9.4 | 9.5 | 9.6 | 9.7 | 9.8 | 9.85 | 9.9 |
| 14 | Stack modernity | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.5 |

Wave 8 program mean ~9.8 — **retracted** after code audit 2026-07-08.

</details>

---

## Update process

1. After each release tag, re-run **code audit**: drivers matrix, stub grep (`source.*stub`, `mode.*stub`), CI gates, integration tests, dated `docs/evidence/` lab archives.
2. Update **Code verified** column in this file **and** the RU twin; note delta in release notes / roadmap History.
3. Program wave columns (if used) track BL shipment only — never substitute for code verified.
4. Link evidence to tests, `packages/` paths, and evidence JSON/MD — not roadmap claims alone.
5. Lab PASS items in [parked-backlog](parked-backlog.md) become scorecard inputs only on a **named full audit** (this document).
6. Refresh AI `competitiveGapIndex` via `python tools/ai-pack/build.py` after matrix edits.
