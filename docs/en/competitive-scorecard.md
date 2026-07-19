# ISPF competitive scorecard (BL-189)

> **Status:** Stable — Code-verified readiness. Hub: [doc-status.md](doc-status.md).

Public readiness matrix for Phases 25–33 (see unified [roadmap](roadmap.md)).

**Version pin (do not conflate):**

| Pin | Meaning |
|-----|---------|
| **0.9.102** | Last **full code audit** of this scorecard (July 2026). Scores and evidence tables below are frozen to that audit. |
| **0.9.105+** | Typical current `main` / prod demostand label in roadmap and deploy docs. **Not** a re-scored matrix — ship notes may advance without reopening every dimension. |

**Current column:** **Code verified** — evidence from `main` source and tests at audit baseline **0.9.102**.

Scale **1–10** vs leading platforms: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, mature context-tree IIoT platforms.

| # | Dimension | Baseline (Phase 24) | **Code verified (0.9.102)** | Target | Phase / BL |
|---|-----------|:-------------------:|:---------------------------:|:------:|------------|
| 1 | Unified data model (object tree) | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-25--ot-trust), [roadmap](roadmap.md#phase-29--mes-platform), [roadmap](roadmap.md#phase-30--automation-depth) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / mimics | 7.0 | **7.5** | **10** | [roadmap](roadmap.md#phase-26--hmi-excellence) — BL-146…152 |
| 3 | OT/IT connectivity (drivers.md) | 6.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-25--ot-trust) — BL-140…145; BL-191 honesty Done |
| 4 | Historian / time-series | 7.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-28--historian-at-scale) — BL-159…163; [roadmap](roadmap.md#phase-33--analytics-platform-af-capable) BL-200…210 |
| 5 | Automation / alarms | 7.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-27--enterprise-security), [roadmap](roadmap.md#phase-30--automation-depth) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-30--automation-depth) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.5** | **10** | [roadmap](roadmap.md#phase-29--mes-platform) — BL-164…170 |
| 8 | Low-code velocity | 8.0 | **8.0** | **10** | [roadmap](roadmap.md#phase-26--hmi-excellence), [roadmap](roadmap.md#phase-31--ai-autopilot) — BL-146…152, BL-177…180 |
| 9 | AI-assisted development | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-31--ai-autopilot) — BL-177…182 |
| 10 | Security / RBAC / tenancy | 6.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-27--enterprise-security) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-25--ot-trust), [roadmap](roadmap.md#phase-28--historian-at-scale), [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-144…145, BL-186…187 |
| 12 | Ecosystem / marketplace | 4.0 | **5.0** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.5** | **10** | maintain — Spring Boot 4, React 19, NATS, ClickHouse option |

**Overall (code verified): ~7.5/10** — simple mean of the 14 dimensions above.

**Post-audit delta (2026-07-19):** AI-assisted development **7.0 → 8.5** after full live suite evidence (BL-178: 52/52 @100%). Other dimensions remain at the **0.9.102** audit baseline.

**Program wave 8 (Jul 2026)** previously claimed ~9.8/10 from shipped BL artifacts; **code audit (0.9.102)** supersedes that figure. See [§ Code audit evidence](#code-audit-evidence-0102) and [§ Gaps to target](#gaps-to-target).

**Docs hygiene (Waves A–D, Jul 2026):** hub paths, AGPL/auth honesty, status tags, and link audit improved DX packaging. Dimension **13 Documentation / DX** stays **8.5** until the next full code audit re-scores it — do not invent a new overall from README work alone.

---

## Definition of done (10/10 overall)

From [roadmap](roadmap.md) (Phases 25–33 / DoD). Status on **0.9.102**:

| Criterion | Status |
|-----------|--------|
| All **14 dimensions ≥9.5**, none ≤8 (BL-189) | **Not met** — code verified mean ~7.5; highest 9.5 (stack modernity) |
| Agent regression **≥95% green** with live LLM (BL-178) | **Met** — full live suite `AGENT_LIVE_SUITE_MODE=full` via `run-live-suite.sh`: **52/52 @100%** (`build/agent-regression/live-suite-results.json`, ~2026-07-18/19). Nightly CI still **platform** mode. `nightly-stub-results.json` **deprecated** (not pass proof) |
| Marketplace GA checklist complete (BL-183) | **Partial** — browse/install/sign/version Shipped; remaining checklist items 11 (live partner catalogs) + 12 (publish CI); BL-184/185 Done |
| Competitive scorecard published per release (BL-189) | **Met** — this document |

---

## Code audit evidence (0.9.102)

Evidence classes: **REAL** (runtime + tests), **PARTIAL** (core works, known gaps), **STUB** (explicit placeholder in source).

| # | Dimension | Score | Assessment | Key evidence |
|---|-----------|:-----:|---------|--------------|
| 1 | Unified data model | 8.5 | **REAL** | Object tree, CEL, blueprints; `AlertRuleListener` → `AlertRuleService`; correlators in `EventCorrelatorService` |
| 2 | SCADA / HMI | 7.5 | **REAL** | `ispf-pid-v1` manifest `totalSymbols: 218`; `ScadaMimicEditor.tsx`; video wall in `dashboardLayoutPresets.ts`; e2e FPS in `quality-gates.spec.ts` (mocked operator API) |
| 3 | OT/IT drivers | 7.0 | **PARTIAL** | 16 `PRODUCTION` in `DriverProductionMatrix` (BL-191 honesty); Milo OPC UA, j2mod, S7, BACnet4J real; `opc-da` / `opc-bridge` / `ethernet-ip` / `dnp3` honest **BETA** (shells / poll-only); DNP3 `writePoint` still throws |
| 4 | Historian | 7.0 | **PARTIAL** | `ClickHouseVariableHistoryStore` HTTP insert/query; JDBC default; BL-210 lab gates + JVM multi-tag gate **defined** (`analytics-scale-gate.sh`); score **≥9.5** after Enterprise L lab sign-off |
| 5 | Automation / alarms | 7.5 | **PARTIAL** | Alert rules + correlators **REAL**; `AlarmShelfApprovalService` in-memory **stub** |
| 6 | Workflow / BPMN | 7.5 | **REAL** | `WorkflowEngineSubProcessTest`, `WorkflowEngineMessageTest`; not full BPMN 2.0 spec |
| 7 | MES / ISA-95 | 6.5 | **PARTIAL** | MES is marketplace product (`mes-platform`, vendor IoT Solutions); base platform does not seed `root.platform.mes`. Bundle JSON/SQL/script BFF; no standalone MES engine module |
| 8 | Low-code velocity | 8.0 | **REAL** | Dashboard builder, bundle deploy (`MarketplaceLocalBundleService.installLocalBundle`), spreadsheets |
| 9 | AI-assisted dev | 8.5 | **REAL** | BL-178 full live suite **52/52 @100%** (`build/agent-regression/live-suite-results.json`, `mode=full`, ~2026-07-18/19); one-shot deploy (`AgentLiveDeploySmokeTest`) + solution apply (`AiSolutionGeneratorLiveSmokeTest`, `mode=live`); draft fallback `mode=draft` (not stub); BL-177/180 multi-app / GA still hardening |
| 10 | Security / RBAC | 7.5 | **PARTIAL** | TOTP MFA + `required-for-admin` **REAL**; `TenantIsolationValidator` **stub** |
| 11 | Deploy / scale / edge | 7.0 | **PARTIAL** | Federation services in `com.ispf.server.federation.*`; Helm skeleton; no CI load proof for cluster scale |
| 12 | Ecosystem / marketplace | 5.5 | **PARTIAL** | Local/remote catalog install **REAL**; partner directory + enroll `"source": "db"` (BL-184 Done, 3 seeded); symbol packs BL-185 Done (`MarketplaceSymbolListingService` `"source": "bundled"` \| `"local"`); live partner catalogs + publish CI still open (BL-183); Partner Portal external |
| 13 | Documentation / DX | 8.5 | **REAL** | Docs + ADRs; code comments mark stubs honestly |
| 14 | Stack modernity | 9.5 | **REAL** | Spring Boot 4, React 19, ClickHouse option in `gradle.properties` / `application.yml` |

### Known code integrity issues (fix before raising OT score)

| Issue | Location |
|-------|----------|
| DNP3 poll-only (BETA) — `writePoint` not implemented | `Dnp3DeviceDriver.writePoint()` — maturity honest; write still open |
| Partner Portal still external | `PartnerProgramService` persists directory/enroll (`source=db`); portal sync out of repo |

---

## Gaps to target

Priority fixes that move **code verified** scores toward 10/10 (not marketing claims). Full domain audit: [roadmap.md § Domain gap audit](roadmap.md#domain-gap-audit--iot--scada--mes--erp-2026-07-09).

1. **OT drivers (7.0 → 9+):** matrix honesty **closed (BL-191)**; remaining gap is field pilot sign-offs + DNP3 write / full DA stacks after **named field driver task** (BL-140 Partial).
2. **ERP L4 / MES (6.5 → 9+):** live 1C or SAP connector (**BL-169** P0); production MES sites. Genealogy lite (**BL-193**) shipped on `mes-platform` (seed graph + Operator report).
3. **AI (8.5 → 9+):** BL-178 full live ≥95% met; harden BL-177 multi-app deploy + broaden generator beyond catalog/one-shot (BL-180); keep full-suite re-runs on demand (`AGENT_LIVE_SUITE_MODE=full`).
4. **Ecosystem (5.5 → 9+):** live partner marketplace catalogs + publish CI gate (BL-183 items 11–12); Partner Portal sync (out of repo).
5. **Historian (7.0 → 9+):** run Enterprise L lab gates (`deploy/local/tools/analytics-scale-gate.sh`, 50k catalog, 1B CH) — BL-210; then update scorecard to **≥9.5** with dated sign-off.
6. **HMI (7.5 → 9+):** FPS gate on live WebSocket mimic path; alarm shelving persistence.
7. **Compliance:** IEC 62443 / GAMP-lite tender pack (**BL-192**) — **docs Done:** [compliance-tender-pack](compliance-tender-pack.md). Remaining gaps: pen-test report, audit-trail GA (SIEM), hard tenancy A≠B, no product certification claim.

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
| 10 | Security / RBAC | 7.2 | 7.3 | 7.4 | 8.5 | 8.8 | 9.3 | 9.5 | 9.7 |
| 11 | Deploy / scale / edge | 8.3 | 8.6 | 9.0 | 9.2 | 9.3 | 9.5 | 9.6 | 9.7 |
| 12 | Ecosystem / marketplace | 5.0 | 5.5 | 6.0 | 8.5 | 8.7 | 9.0 | 9.3 | 9.6 |
| 13 | Documentation / DX | 9.3 | 9.4 | 9.5 | 9.6 | 9.7 | 9.8 | 9.85 | 9.9 |
| 14 | Stack modernity | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.5 |

Wave 8 program mean ~9.8 — **retracted** after code audit 2026-07-08.

</details>

---

## Update process

1. After each release tag, re-run **code audit**: drivers matrix, stub grep (`source.*stub`, `mode.*stub`), CI gates, integration tests.
2. Update **Code verified** column in this file; note delta in release notes.
3. Program wave columns (if used) track BL shipment only — never substitute for code verified.
4. Link evidence to tests and `packages/` paths, not roadmap claims alone.
