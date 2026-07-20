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
| 9 | AI-assisted development | 9.0 | **9.0** | **10** | [roadmap](roadmap.md#phase-31--ai-autopilot) — BL-177…182 |
| 10 | Security / RBAC / tenancy | 6.5 | **8.0** | **10** | [roadmap](roadmap.md#phase-27--enterprise-security) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-25--ot-trust), [roadmap](roadmap.md#phase-28--historian-at-scale), [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-144…145, BL-186…187 |
| 12 | Ecosystem / marketplace | 4.0 | **5.0** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.5** | **10** | maintain — Spring Boot 4, React 19, NATS, ClickHouse option |

**Overall (code verified): ~7.6/10** — simple mean of the 14 dimensions above.

**Post-audit delta (2026-07-19):** AI-assisted development **7.0 → 8.5** after full live suite (BL-178: 52/52 @100%), then **8.5 → 9.0** after BL-177/180 multi-app / multi-domain live smoke harness Done (opt-in `ISPF_LLM_SMOKE`; no invented multi-app/multi-domain live pass counts). Ecosystem / marketplace **5.0 → 6.5** and Deploy / scale / edge **7.0 → 7.5** after Phase 32 BL-183/186/187/188 close (CI catalog gate, Helm smoke, ARM edge, MoM usable path) — evidence table below; frozen top matrix still shows **0.9.102** until next full audit.

**Program wave 8 (Jul 2026)** previously claimed ~9.8/10 from shipped BL artifacts; **code audit (0.9.102)** supersedes that figure. See [§ Code audit evidence](#code-audit-evidence-0102) and [§ Gaps to target](#gaps-to-target).

**Docs hygiene (Waves A–D, Jul 2026):** hub paths, AGPL/auth honesty, status tags, and link audit improved DX packaging. Dimension **13 Documentation / DX** stays **8.5** until the next full code audit re-scores it — do not invent a new overall from README work alone.

---

## Definition of done (10/10 overall)

From [roadmap](roadmap.md) (Phases 25–33 / DoD). Status on **0.9.102**:

| Criterion | Status |
|-----------|--------|
| All **14 dimensions ≥9.5**, none ≤8 (BL-189) | **Not met** — code verified mean ~7.6; highest 9.5 (stack modernity) |
| Agent regression **≥95% green** with live LLM (BL-178) | **Met** — full live suite `AGENT_LIVE_SUITE_MODE=full` via `run-live-suite.sh`: **52/52 @100%** (`build/agent-regression/live-suite-results.json`, ~2026-07-18/19). Nightly CI still **platform** mode. `nightly-stub-results.json` **deprecated** (not pass proof) |
| Marketplace GA checklist complete (BL-183) | **Met** — browse/install/sign/version + honest partner multi-endpoint (11) + CI catalog gate (12); BL-184/185 Done; Partner Portal external |
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
| 5 | Automation / alarms | 7.5 | **PARTIAL** | Alert rules + correlators **REAL**; alarm shelving approval **persisted** (BL-158) |
| 6 | Workflow / BPMN | 7.5 | **REAL** | `WorkflowEngineSubProcessTest`, `WorkflowEngineMessageTest`; not full BPMN 2.0 spec |
| 7 | MES / ISA-95 | 6.5 | **PARTIAL** | MES is marketplace product (`mes-platform`, vendor IoT Solutions); base platform does not seed `root.platform.mes`. Bundle JSON/SQL/script BFF; no standalone MES engine module |
| 8 | Low-code velocity | 8.0 | **REAL** | Dashboard builder, bundle deploy (`MarketplaceLocalBundleService.installLocalBundle`), spreadsheets |
| 9 | AI-assisted dev | 9.0 | **REAL** | BL-178 full live suite **52/52 @100%** (`build/agent-regression/live-suite-results.json`, `mode=full`, ~2026-07-18/19); BL-177 multi-app `AgentLiveDeploySmokeTest` matrix (`mes-platform`, `building-hvac`, `platform-primitive`); BL-180 multi-domain `AiSolutionGeneratorLiveSmokeTest` (HVAC/MES/SCADA, `composition=primitives`); draft fallback `mode=draft` (not stub); live smoke runs require `ISPF_LLM_SMOKE=true` |
| 10 | Security / RBAC | 8.5 | **PARTIAL** | TOTP MFA GA **Done** (BL-153); per-var/event/function ACL **Done** (BL-154); audit SIEM + role-template scopes **Done** (BL-156/157); SaaS tenant-admin + logical A≠B path/API **Done** (BL-155); hard schema table routing still optional; WebAuthn → BL-194 |
| 11 | Deploy / scale / edge | 7.5 | **PARTIAL** | Federation MoM usable path **REAL** (BL-188); Helm lint/template + ARM edge compose **Done** (BL-186/187); no CI load proof for cluster / 10+ peer scale |
| 12 | Ecosystem / marketplace | 6.5 | **PARTIAL** | Marketplace GA **Done** (BL-183 — multi-endpoint + CI catalog gate); partner directory + enroll `"source": "db"` (BL-184); symbol packs BL-185 Done; Partner Portal sync still external |
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
2. **ERP L4 / MES (6.5 → 9+):** live 1C or SAP connector (**BL-169** deferred); production MES sites. Marketplace lab Done: BL-164…168, BL-170, BL-193 on `mes-platform` v1.3.0 (typed seeds + Operator/BFF dashboards). Score stays **6.5** until field site + live ERP.
3. **AI (9.0 → 10):** BL-177…180 Done (harness + BL-178 52/52); soft-budget **evidence path** shipped (`run-live-generator-oneshot.sh` → `live-generator-results.json`, integrator checklist in [ai-agent](ai-agent.md#bl-180-soft-15-min--integrator-oneshot-field-soak-ready)). Remaining: dated real oneshot JSON with `softBudgetMet: true` + named-site field soak journal (lab oneshot ≠ field Done). Keep full-suite re-runs on demand (`AGENT_LIVE_SUITE_MODE=full`).
4. **Ecosystem (6.5 → 9+):** Partner Portal sync + live partner-hosted catalogs (out of repo); raise after first external partner catalog onboarding.
5. **Historian (7.0 → 9+):** run Enterprise L lab gates (`deploy/local/tools/analytics-scale-gate.sh`, 50k catalog, 1B CH) — BL-210; then update scorecard to **≥9.5** with dated sign-off.
6. **HMI (7.5 → 9+):** FPS gate on live WebSocket mimic path (alarm shelving persistence closed — BL-158).
7. **Compliance:** IEC 62443 / GAMP-lite tender pack (**BL-192**) — **docs Done:** [compliance-tender-pack](compliance-tender-pack.md). Remaining gaps: pen-test report, optional hard schema table routing (BL-155 logical SaaS Done), WebAuthn (BL-194), no product certification claim. SIEM audit webhook shipped (BL-156).

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
