# ISPF competitive scorecard (BL-189)

Public readiness matrix for Phases 25–33 (see unified [roadmap](roadmap.md)). **Current column:** **Code verified** — evidence from `main` source and tests (release **0.9.102**, July 2026).

Scale **1–10** vs leading platforms: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, mature context-tree IIoT platforms.

| # | Dimension | Baseline (Phase 24) | **Code verified (0.9.102)** | Target | Phase / BL |
|---|-----------|:-------------------:|:---------------------------:|:------:|------------|
| 1 | Unified data model (object tree) | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-25--ot-trust), [roadmap](roadmap.md#phase-29--mes-platform), [roadmap](roadmap.md#phase-30--automation-depth) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / mimics | 7.0 | **7.5** | **10** | [roadmap](roadmap.md#phase-26--hmi-excellence) — BL-146…152 |
| 3 | OT/IT connectivity (drivers.md) | 6.0 | **6.5** | **10** | [roadmap](roadmap.md#phase-25--ot-trust) — BL-140…145 |
| 4 | Historian / time-series | 7.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-28--historian-at-scale) — BL-159…163; [roadmap](roadmap.md#phase-33--analytics-platform-af-capable) BL-200…210 |
| 5 | Automation / alarms | 7.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-27--enterprise-security), [roadmap](roadmap.md#phase-30--automation-depth) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-30--automation-depth) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.5** | **10** | [roadmap](roadmap.md#phase-29--mes-platform) — BL-164…170 |
| 8 | Low-code velocity | 8.0 | **8.0** | **10** | [roadmap](roadmap.md#phase-26--hmi-excellence), [roadmap](roadmap.md#phase-31--ai-autopilot) — BL-146…152, BL-177…180 |
| 9 | AI-assisted development | 9.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-31--ai-autopilot) — BL-177…182 |
| 10 | Security / RBAC / tenancy | 6.5 | **7.5** | **10** | [roadmap](roadmap.md#phase-27--enterprise-security) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **7.0** | **10** | [roadmap](roadmap.md#phase-25--ot-trust), [roadmap](roadmap.md#phase-28--historian-at-scale), [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-144…145, BL-186…187 |
| 12 | Ecosystem / marketplace | 4.0 | **5.0** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#phase-32--ecosystem--market) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.5** | **10** | maintain — Spring Boot 4, React 19, NATS, ClickHouse option |

**Overall (code verified): ~7.4/10** — simple mean of the 14 dimensions above.

**Program wave 8 (Jul 2026)** previously claimed ~9.8/10 from shipped BL artifacts; **code audit (0.9.102)** supersedes that figure. See [§ Code audit evidence](#code-audit-evidence-0102) and [§ Gaps to target](#gaps-to-target).

---

## Definition of done (10/10 overall)

From [roadmap](roadmap.md) (Phases 25–33 / DoD). Status on **0.9.102**:

| Criterion | Status |
|-----------|--------|
| All **14 dimensions ≥9.5**, none ≤8 (BL-189) | **Not met** — code verified mean ~7.4; highest 9.5 (stack modernity) |
| Agent regression **≥95% green** with live LLM (BL-178) | **Not met** — schema CI + optional **REAL** one-shot (`run-live-oneshot.sh` / `AgentLiveDeploySmokeTest`); full 50@≥95% open. `nightly-stub-results.json` **deprecated** (not pass proof) |
| Marketplace GA checklist complete (BL-183) | **Partial** — local bundle install real; partner/symbol/cert paths stub |
| Competitive scorecard published per release (BL-189) | **Met** — this document |

---

## Code audit evidence (0.9.102)

Evidence classes: **REAL** (runtime + tests), **PARTIAL** (core works, known gaps), **STUB** (explicit placeholder in source).

| # | Dimension | Score | Assessment | Key evidence |
|---|-----------|:-----:|---------|--------------|
| 1 | Unified data model | 8.5 | **REAL** | Object tree, CEL, blueprints; `AlertRuleListener` → `AlertRuleService`; correlators in `EventCorrelatorService` |
| 2 | SCADA / HMI | 7.5 | **REAL** | `ispf-pid-v1` manifest `totalSymbols: 218`; `ScadaMimicEditor.tsx`; video wall in `dashboardLayoutPresets.ts`; e2e FPS in `quality-gates.spec.ts` (mocked operator API) |
| 3 | OT/IT drivers | 6.5 | **PARTIAL** | 20 `PRODUCTION` in `DriverProductionMatrix`; Milo OPC UA, j2mod, S7, BACnet4J real; `opc-da` class doc **stub** but matrix PRODUCTION; DNP3 `writePoint` throws |
| 4 | Historian | 7.0 | **PARTIAL** | `ClickHouseVariableHistoryStore` HTTP insert/query; JDBC default; BL-210 lab gates + JVM multi-tag gate **defined** (`analytics-scale-gate.sh`); score **≥9.5** after Enterprise L lab sign-off |
| 5 | Automation / alarms | 7.5 | **PARTIAL** | Alert rules + correlators **REAL**; `AlarmShelfApprovalService` in-memory **stub** |
| 6 | Workflow / BPMN | 7.5 | **REAL** | `WorkflowEngineSubProcessTest`, `WorkflowEngineMessageTest`; not full BPMN 2.0 spec |
| 7 | MES / ISA-95 | 6.5 | **PARTIAL** | MES is marketplace product (`mes-platform`, vendor IoT Solutions); base platform does not seed `root.platform.mes`. Bundle JSON/SQL/script BFF; no standalone MES engine module |
| 8 | Low-code velocity | 8.0 | **REAL** | Dashboard builder, bundle deploy (`MarketplaceLocalBundleService.installLocalBundle`), spreadsheets |
| 9 | AI-assisted dev | 7.0 | **PARTIAL** | **REAL** one-shot live LLM deploy (`AgentLiveDeploySmokeTest`) + **REAL** solution apply (`AiSolutionGeneratorLiveSmokeTest`, `mode=live`); draft fallback `mode=draft` (not stub); full BL-178 ≥95% not met |
| 10 | Security / RBAC | 7.5 | **PARTIAL** | TOTP MFA + `required-for-admin` **REAL**; `TenantIsolationValidator` **stub** |
| 11 | Deploy / scale / edge | 7.0 | **PARTIAL** | Federation services in `com.ispf.server.federation.*`; Helm skeleton; no CI load proof for cluster scale |
| 12 | Ecosystem / marketplace | 5.0 | **PARTIAL** | Local catalog install **REAL**; `PartnerProgramService` / `MarketplaceSymbolListingService` `"source": "stub"` |
| 13 | Documentation / DX | 8.5 | **REAL** | Docs + ADRs; code comments mark stubs honestly |
| 14 | Stack modernity | 9.5 | **REAL** | Spring Boot 4, React 19, ClickHouse option in `gradle.properties` / `application.yml` |

### Known code integrity issues (fix before raising OT score)

| Issue | Location |
|-------|----------|
| `opc-da` marked PRODUCTION but driver is stub | `DriverProductionMatrix.java`, `OpcDaDeviceDriver.java` |
| DNP3 PRODUCTION without write | `Dnp3DeviceDriver.writePoint()` |
| Full agent ≥95% live not measured | Optional one-shot REAL; stub file deprecated — need full live suite for BL-178 |
| Partner enroll is synthetic | `PartnerProgramService.java` — hardcoded demo partners |

---

## Gaps to target

Priority fixes that move **code verified** scores toward 10/10 (not marketing claims). Full domain audit: [roadmap.md § Domain gap audit](roadmap.md#domain-gap-audit--iot--scada--mes--erp-2026-07-09).

1. **OT drivers (6.5 → 9+):** matrix honesty — `opc-da` stub / DNP3 write (**BL-191**); field pilot sign-offs after **named field driver task** (BL-140 Partial).
2. **ERP L4 / MES (6.5 → 9+):** live 1C or SAP connector (**BL-169** P0); production MES sites; genealogy lite (**BL-193**).
3. **AI (7.0 → 9+):** expand beyond S31/S32 one-shots to full live 50@≥95% (`--enforce-rate`); broaden generator beyond catalog templates.
4. **Ecosystem (5.0 → 9+):** persist partner enrollments; symbol pack install beyond in-memory stub; external signed bundles.
5. **Historian (7.0 → 9+):** run Enterprise L lab gates (`deploy/local/tools/analytics-scale-gate.sh`, 50k catalog, 1B CH) — BL-210; then update scorecard to **≥9.5** with dated sign-off.
6. **HMI (7.5 → 9+):** FPS gate on live WebSocket mimic path; alarm shelving persistence.
7. **Compliance:** IEC 62443 / GAMP-lite tender pack (**BL-192**).

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
