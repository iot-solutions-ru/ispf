> **Language:** Canonical English. Russian edition: [ru/roadmap-phase-25.md](../ru/roadmap-phase-25.md).

# ISPF Platform Roadmap — Phase 25–32 (Excellence Program)

**Goal:** bring the product to **10/10** across all competitive comparison dimensions (IoT / SCADA / MES low-code) and surpass incumbents in solution delivery speed and AI-native development.

| | |
| --- | --- |
| **Baseline** | Phase 24 closed, `main`, July 2026 |
| **Updated** | 2026-07-08 (code audit 0.9.102) |
| **Previous phases** | [roadmap.md](roadmap.md) — Phase 0–24, BL-01…139, S01–S30 |
| **North star** | Open self-hosted industrial application platform — object tree + SCADA HMI + automation + apps + AI ([architecture.md](architecture.md)) |

---

## Summary

| Category | Total | Done | Partial | Planned | Cancelled |
| --------- | ----- | ---- | ------- | ------- | --------- |
| Phase 25–32 | 8 | 0 | 8 | 0 | — |
| BL-140…190 | 51 | 27 | 24 | 0 | 0 |
| Sprint S31–S46 (draft) | 16 | 0 | 16 | 0 | — |

**Current sprint:** **S31–S46** — Excellence Program waves 1–8 shipped; **code audit** supersedes program score.

**Current product score (code verified, 0.9.102):** **~7.4/10** — see [competitive-scorecard.md](competitive-scorecard.md).  
**Target score:** 10/10 — see [§ Definition of Done](#definition-of-done--1010-overall) (**not achieved**).

---

## Competitive scorecard (baseline → code verified → target)

Scale 1–10 relative to best-in-class platforms (Ignition / Kepware / PI / Opcenter / Tulip / mature context-tree IIoT).  
**Code verified** — evidence from `main` source/tests (0.9.102). Full matrix: [competitive-scorecard.md](competitive-scorecard.md).

| Dimension | Baseline | **Code verified** | Target | Phase |
| --------- | :------: | :---------------: | :----: | ----- |
| Unified data model | 9.0 | **8.5** | **10** | 25, 29, 30 |
| SCADA / HMI / mimics | 7.0 | **7.5** | **10** | 26 |
| OT/IT connectivity (drivers) | 6.0 | **6.5** | **10** | 25 |
| Historian / time-series | 7.0 | **7.0** | **10** | 28 |
| Automation / alarms | 7.5 | **7.5** | **10** | 27, 30 |
| Workflow / BPMN | 6.5 | **7.5** | **10** | 30 |
| MES / ISA-95 | 5.5 | **6.5** | **10** | 29 |
| Low-code velocity | 8.0 | **8.0** | **10** | 26, 31 |
| AI-assisted development | 9.0 | **6.5** | **10** | 31 |
| Security / RBAC / tenancy | 6.5 | **7.5** | **10** | 27 |
| Deploy / scale / edge | 8.0 | **7.0** | **10** | 25, 28, 32 |
| Ecosystem / marketplace | 4.0 | **5.0** | **10** | 32 |
| Documentation / DX | 9.0 | **8.5** | **10** | 32 |
| Stack modernity | 9.0 | **9.5** | **10** | maintain |

**Overall (code verified): ~7.4/10**

---

## Priorities (when resources are limited)

| Priority | Phase | Why |
| --------- | ----- | ------ |
| **P0** | [25 — OT Trust](#phase-25--ot-trust) | Without OT engineer trust, the product will not be accepted on production sites |
| **P0** | [31 — AI Autopilot](#phase-31--ai-autopilot) | The only moat incumbents cannot copy within a year |
| **P1** | [26 — HMI Excellence](#phase-26--hmi-excellence) | SCADA is the operator-facing face of the product |
| **P1** | [29 — MES Platform](#phase-29--mes-platform) | Differentiates from "just SCADA" |
| **P2** | [27 — Enterprise Security](#phase-27--enterprise-security) | Enterprise tender requirement |
| **P2** | [28 — Historian at Scale](#phase-28--historian-at-scale) | Large sites, petabyte-class |
| **P3** | [30 — Automation Depth](#phase-30--automation-depth) | Power users, CEP, process control |
| **P3** | [32 — Ecosystem & Market](#phase-32--ecosystem--market) | Scale through partners |

---

## Sprint registry (draft)

| Sprint | Phase | Theme | BL / scope | Status |
| ------ | ----- | ---- | ---------- | ------ |
| S31 | 25 | OT Trust wave 1 | BL-140, BL-141 | Partial |
| S32 | 25 | OT Trust wave 2 | BL-142, BL-143 | Partial |
| S33 | 25 | Edge + DDK | BL-144, BL-145 | Partial |
| S34 | 26 | HMI symbols + debugger | BL-146, BL-149 | Partial |
| S35 | 26 | HMI perf + video wall | BL-147, BL-148, BL-152 | Partial |
| S36 | 26 | Operator offline + spreadsheet | BL-150, BL-151 | Partial |
| S37 | 27 | MFA + per-variable ACL | BL-153, BL-154 | Partial |
| S38 | 27 | Hard tenancy + audit | BL-155, BL-156, BL-157, BL-158 | Partial |
| S39 | 28 | Historian tiers | BL-159, BL-160 | Partial |
| S40 | 28 | Historian scale lab | BL-161, BL-162, BL-163 | Partial |
| S41 | 29 | MES objects + OEE | BL-164, BL-165 | Partial |
| S42 | 29 | MES dispatch + quality | BL-166, BL-167, BL-168 | Partial |
| S43 | 30 | CEP + process control | BL-171, BL-172, BL-173 | Partial |
| S44 | 31 | AI e2e deploy | BL-177, BL-178 | Partial |
| S45 | 31 | AI solution generator | BL-179, BL-180, BL-181 | Partial |
| S46 | 32 | Marketplace + partners | BL-183, BL-184, BL-189 | Partial |

Guideline: **~2 weeks per sprint**; Phase 25–32 ≈ **18–24 months**.

---

## Phase 25 — OT Trust

**Goal:** OT/IT connectivity **10/10** — production-grade drivers, interop lab, edge agents, DDK.

**Gap today:** 58 `driverId` entries in catalog, ~13 `PRODUCTION` in [DriverProductionMatrix](../packages/ispf-server/src/main/java/com/ispf/server/driver/DriverProductionMatrix.java).

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-140 | **Top-20 industrial PRODUCTION** | P0 | Modbus×3, OPC UA, OPC UA server, S7, BACnet, MQTT, SNMP, HTTP, flexible, IEC-104, DNP3, DLMS, EtherNet/IP, OPC DA bridge, GPS — `DriverMaturity.PRODUCTION`, interop test green, [drivers.md](drivers.md) updated |
| BL-141 | **Driver interop lab** | P0 | Docker fixtures per driver, CI workflow `driver-interop.yml`, latency + write round-trip report |
| BL-142 | **Event→variable at driver** | P1 | MQTT/Kafka streams → dynamic variables; integration test |
| BL-143 | **OPC UA server GA** | P1 | External UA clients: subscribe, read, write; interop with UA Expert / prosys |
| BL-144 | **Driver DDK** | P1 | `packages/ispf-driver-ddk`, template, 3 reference custom drivers, [driver-promotion.md](driver-promotion.md) |
| BL-145 | **Agent edge GA** | P1 | Store-forward, offline buffer, federation sync — 30-day field soak, [federation.md](federation.md) |

**Phase metric:** 20 PRODUCTION drivers; 0 beta in top-industrial list; 3 pilot OT sites without middleware.

**Related docs:** [drivers.md](drivers.md), [driver-promotion.md](driver-promotion.md), [ADR-0022](decisions/0022-driver-production-matrix.md).

---

## Phase 26 — HMI Excellence

**Goal:** SCADA / HMI **10/10** — P&ID library, video wall, offline operator, expression debugger.

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-146 | **P&ID symbol library v2** | P1 | 200+ ISA symbols, import pipeline, [scada-symbol-library.md](scada-symbol-library.md), legal audit |
| BL-147 | **Mimic editor pro** | P1 | Multi-select, layers, undo/redo, keyboard nav — WCAG |
| BL-148 | **Video wall mode** | P2 | Dashboard layout 2×2…4×4, auto-scale |
| BL-149 | **Expression debugger** | P1 | Step-through CEL/bindings in Web Console, breakpoints |
| BL-150 | **Live spreadsheet v2** | P2 | Real-time cell refresh, cross-sheet refs, export — [spreadsheet-widget.md](spreadsheet-widget.md) |
| BL-151 | **Operator offline PWA** | P1 | Service worker: dashboards + mimics cache; sync on reconnect |
| BL-152 | **HMI perf gate** | P1 | Mimic 500 elements ≥60 FPS; Lighthouse operator ≥95 — [hmi-quality-gates.md](hmi-quality-gates.md) |

**Phase metric:** mini-TEC + pipeline SCADA on video wall; operator 8 h offline.

**Related docs:** [scada.md](scada.md), [widgets.md](widgets.md), [hmi-quality-gates.md](hmi-quality-gates.md).

---

## Phase 27 — Enterprise Security

**Goal:** Security / RBAC **10/10** — MFA, per-variable ACL, hard tenancy, audit.

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-153 | **MFA** | P2 | TOTP + WebAuthn; Keycloak integration — [security.md](security.md) |
| BL-154 | **Per-variable ACL** | P2 | read/write on variable, event, function (not only object-level) |
| BL-155 | **Hard multi-tenancy** | P2 | Per-tenant DB schema option; OIDC tenant claim mapping — [multi-tenant.md](multi-tenant.md) |
| BL-156 | **Audit trail GA** | P2 | Immutable audit log, export, SIEM webhook |
| BL-157 | **Role templates** | P2 | Custom roles; ISA-95 scoped permissions |
| BL-158 | **Alarm shelving** | P2 | Shelve/unshelve with approval workflow — extension of [automation.md](automation.md) |

**Phase metric:** pentest pass; tenant A ≠ tenant B in hard mode; MFA mandatory for admin.

---

## Phase 28 — Historian at Scale

**Goal:** Historian **10/10** — turnkey tiers, asset analytics, petabyte path.

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-159 | **Historian tiers turnkey** | P2 | Hot (PG/Timescale) → Warm (CH) → Cold (S3/parquet); one-click deploy profile |
| BL-160 | **Asset analytics framework** | P2 | Rollups, KPI templates, derived tags (AF-like lite) |
| BL-161 | **Historian query SLA** | P2 | 1M points aggregate <2s; documented SLO |
| BL-162 | **Event journal petabyte path** | P2 | CH cutover playbook executed; lab 10M events/min — [clickhouse-prod-playbook.md](clickhouse-prod-playbook.md) |
| BL-163 | **Trend export** | P3 | Excel/CSV/Parquet bulk, REST streaming — [variable-history.md](variable-history.md) |

**Phase metric:** lab 1B samples query; prod playbook ≤5 manual steps.

**Related ADRs:** [0016](decisions/0016-clickhouse-event-journal.md), [0035](decisions/0035-historian-dual-write.md), [0025](decisions/0025-cassandra-scylla-timeseries-store.md).

---

## Phase 29 — MES Platform

**Goal:** MES / ISA-95 **10/10** — first-class MES objects, not only reference bundles.

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-164 | **MES object types** | P1 | `WORK_ORDER`, `OPERATION`, `LOT`, `SHIFT`, `QUALITY_RECORD` in tree |
| BL-165 | **OEE first-class** | P1 | Platform BFF + dashboards; ISA-95 paths — [isa95-catalog.md](isa95-catalog.md) |
| BL-166 | **Work order dispatch** | P1 | BPMN + work-queue + mobile operator confirm |
| BL-167 | **Quality module** | P2 | SPC charts, defect tracking, traceability report |
| BL-168 | **ISA-88 batch lite** | P2 | Recipe + phase + batch instance (workflow-backed) |
| BL-169 | **ERP outbox** | P3 | SAP/1C connector pattern, idempotent sync |
| BL-170 | **MES certification bundle** | P1 | `mes-platform` bundle — deploy ≤30 min — [reference-mes-oee-walkthrough.md](reference-mes-oee-walkthrough.md) |

**Phase metric:** OEE walkthrough → production MES in 1 day without custom Java.

---

## Phase 30 — Automation Depth

**Goal:** Automation + workflow **10/10** — CEP, process control, queries, BPMN expansion.

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-171 | **CEP engine** | P3 | Windowed patterns beyond COUNT/SEQUENCE (A→B within T) |
| BL-172 | **Process control context** | P3 | `root.platform.process-programs` — cyclic control loops |
| BL-173 | **Queries engine** | P2 | Dynamic cross-object queries in tree |
| BL-174 | **Event filters** | P3 | Reusable event log filters |
| BL-175 | **ML hooks** | P3 | Anomaly detection SPI + reference model |
| BL-176 | **BPMN expansion** | P2 | Message events, escalation, compensation, DMN lite — [workflows.md](workflows.md) |

**Phase metric:** escalation + CEP + process program in one project without ad-hoc scripts.

---

## Phase 31 — AI Autopilot

**Goal:** AI **10/10** — zero-touch deploy, agent regression, solution generator.

**ISPF strength — amplify, do not regress.**

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-177 | **End-to-end agent deploy** | P0 | Agent: spec → bundle → deploy → operator UI without human edit |
| BL-178 | **Agent regression suite** | P0 | 50 scenarios CI (MES, SCADA, HVAC) — pass rate ≥95% |
| BL-179 | **Operator agent GA** | P1 | Scoped tools, memory, ru/en — [ai-development.md](ai-development.md) |
| BL-180 | **Solution generator** | P0 | "Describe a plant" → tree + dashboards + alerts <15 min |
| BL-181 | **Agent observability v2** | P2 | Cost/latency per tool; failure auto-retry — [ADR-0034](decisions/0034-agent-observability-and-session-knowledge.md) |
| BL-182 | **Context pack v2** | P2 | Auto-refresh from live platform + readiness gap index |

**Phase metric:** new integrator builds demo in 2 h using only AI Studio.

---

## Phase 32 — Ecosystem & Market

**Goal:** Ecosystem **10/10** — marketplace, partners, K8s, certification.

| ID | Task | Priority | Acceptance |
| -- | ------ | --------- | ---------- |
| BL-183 | **Marketplace GA** | P3 | Browse, install, sign, version bundle — [marketplace.md](marketplace.md) |
| BL-184 | **Partner program** | P3 | 5 certified integrators, training curriculum |
| BL-185 | **Symbol marketplace** | P3 | Community P&ID packs with legal review |
| BL-186 | **K8s Helm chart** | P2 | Production helm + operator — [deployment.md](deployment.md) |
| BL-187 | **ARM edge profile** | P2 | Raspberry Pi / industrial gateway compose — [demostands.md](demostands.md) |
| BL-188 | **Manager-of-managers** | P3 | Federation hub 10+ peers, unified operator shell — [federation.md](federation.md) |
| BL-189 | **Competitive scorecard** | P3 | Public readiness matrix, updated per release |
| BL-190 | **Certification paths** | P3 | Solution developer + platform admin exams |

**Phase metric:** 10+ bundles in marketplace; 3 external partners without core team.

---

## BL-140…190 — full registry

| ID | Phase | Name | P | Status |
| -- | ----- | -------- | - | ------ |
| BL-140 | 25 | Top-20 industrial PRODUCTION | P0 | **Done** (20 PRODUCTION + 3 pilots ready-for-field) |
| BL-141 | 25 | Driver interop lab | P0 | **Done** (Docker + CI smoke) |
| BL-142 | 25 | Event→variable at driver | P1 | Partial |
| BL-143 | 25 | OPC UA server GA | P1 | Partial |
| BL-144 | 25 | Driver DDK | P1 | Partial |
| BL-145 | 25 | Agent edge GA | P1 | Partial (disk buffer; 30d soak) |
| BL-146 | 26 | P&ID symbol library v2 | P1 | **Done** (218 symbols) |
| BL-147 | 26 | Mimic editor pro | P1 | Partial |
| BL-148 | 26 | Video wall mode | P2 | Partial |
| BL-149 | 26 | Expression debugger | P1 | Partial |
| BL-150 | 26 | Live spreadsheet v2 | P2 | Partial |
| BL-151 | 26 | Operator offline PWA | P1 | Partial |
| BL-152 | 26 | HMI perf gate | P1 | Partial (200 el e2e) |
| BL-153 | 27 | MFA | P2 | Partial (TOTP) |
| BL-154 | 27 | Per-variable ACL | P2 | Partial |
| BL-155 | 27 | Hard multi-tenancy | P2 | Partial |
| BL-156 | 27 | Audit trail GA | P2 | Partial |
| BL-157 | 27 | Role templates | P2 | Partial |
| BL-158 | 27 | Alarm shelving | P2 | Partial |
| BL-159 | 28 | Historian tiers turnkey | P2 | Partial |
| BL-160 | 28 | Asset analytics framework | P2 | Partial |
| BL-161 | 28 | Historian query SLA | P2 | Partial |
| BL-162 | 28 | Event journal petabyte path | P2 | Partial |
| BL-163 | 28 | Trend export | P3 | **Done** (Parquet) |
| BL-164 | 29 | MES object types | P1 | Partial |
| BL-165 | 29 | OEE first-class | P1 | Partial |
| BL-166 | 29 | Work order dispatch | P1 | Partial |
| BL-167 | 29 | Quality module | P2 | Partial |
| BL-168 | 29 | ISA-88 batch lite | P2 | Partial |
| BL-169 | 29 | ERP outbox | P3 | Partial |
| BL-170 | 29 | MES certification bundle | P1 | Partial |
| BL-171 | 30 | CEP engine | P3 | Partial |
| BL-172 | 30 | Process control context | P3 | Partial |
| BL-173 | 30 | Queries engine | P2 | Partial |
| BL-174 | 30 | Event filters | P3 | Partial |
| BL-175 | 30 | ML hooks | P3 | Partial |
| BL-176 | 30 | BPMN expansion | P2 | Partial (subprocess stub; message events) |
| BL-177 | 31 | End-to-end agent deploy | P0 | Partial |
| BL-178 | 31 | Agent regression suite | P0 | Partial (50 scenarios schema CI; live ≥95% not met) |
| BL-179 | 31 | Operator agent GA | P1 | Partial |
| BL-180 | 31 | Solution generator | P0 | Partial (keyword stub) |
| BL-181 | 31 | Agent observability v2 | P2 | Partial |
| BL-182 | 31 | Context pack v2 | P2 | Partial |
| BL-183 | 32 | Marketplace GA | P3 | Partial (install/uninstall) |
| BL-184 | 32 | Partner program | P3 | Partial |
| BL-185 | 32 | Symbol marketplace | P3 | Planned |
| BL-186 | 32 | K8s Helm chart | P2 | Partial |
| BL-187 | 32 | ARM edge profile | P2 | Partial |
| BL-188 | 32 | Manager-of-managers | P3 | Partial |
| BL-189 | 32 | Competitive scorecard | P3 | **Done** (published; code verified ~7.4/10) |
| BL-190 | 32 | Certification paths | P3 | Partial |

---

## Competitive matrix: who we overtake

| Competitor class | Their strength | Our counter | Target BL |
| ---------------- | ------------------ | -------------- | --------- |
| Connectivity hub | 150+ protocols, all production | BL-140…145 OT Trust | 10 |
| SCADA suite | HMI + UDT + marketplace | BL-146…152 + BL-183 | 10 |
| Historian | Petabyte + asset framework | BL-159…163 | 10 |
| MES suite | Full MES modules | BL-164…170 | 10 |
| Shopfloor low-code | Apps in hours | BL-177…180 AI | 10 |
| Enterprise IAM | MFA, granular ACL | BL-153…157 | 10 |
| Cloud IoT SaaS | Multi-tenant SaaS | BL-155 + BL-186 | 10 |
| Mature context-tree IIoT | 20 years field track record | All phases + AI moat | 10 |

---

## Definition of Done — 10/10 overall

Product is **10/10** when **all** of the following hold simultaneously:

1. **20 PRODUCTION drivers** with interop CI and 3 field pilots — **pilots ready-for-field** (BL-140 ✅); customer soak pending
2. **MES bundle** — OEE + work orders + quality without custom code (BL-164…170)
3. **AI agent** — ≥95% regression scenarios green **with live LLM**, deploy without edits (BL-177, BL-178) — **not met** (CI: schema only; nightly stub results)
4. **Historian** — turnkey 3-tier, 1B samples lab proven (BL-159…162)
5. **Security** — MFA + per-variable ACL + hard tenancy option (BL-153…155)
6. **HMI** — mimic 500 el @60 FPS, offline PWA 8h (BL-151, BL-152)
7. **Marketplace** — 10+ signed bundles, 3 external partners (BL-183, BL-184)
8. **Scorecard** — all 14 dimensions ≥9.5, none ≤8 (BL-189) — **not met** (code verified ~7.4/10; see [competitive-scorecard.md](competitive-scorecard.md))

---

## What we preserve (no regression)

- Object tree + blueprints (RELATIVE / INSTANCE / ABSOLUTE)
- Tree-first AI agent + MCP native
- BPMN workflow + bundle deploy
- Federation + horizontal cluster
- Brick / Haystack semantic layer
- 70+ docs, ADR culture, lab walkthroughs
- Cloud-native stack (Spring Boot 4, React 19, PostgreSQL)

---

## Next 90 days

| Sprint | Timeline (draft) | Scope |
| ------ | ------------ | ----- |
| **S31** | Aug 2026 | BL-140 top-15 PRODUCTION; BL-141 interop lab skeleton |
| **S32** | Aug 2026 | BL-146 symbols v2 start; BL-149 expression debugger MVP |
| **S33** | Sep 2026 | BL-177 e2e agent deploy; BL-178 20 agent scenarios CI |

---

## Related documents

| Document | Purpose |
| -------- | ---------- |
| [roadmap.md](roadmap.md) | Phase 0–24, BL-01…139 (closed) |
| [acceleration-program.md](acceleration-program.md) | S19–S23 methodology |
| [product.md](product.md) | Product overview |
| [architecture.md](architecture.md) | North star |
| [decisions/](decisions/) | ADRs for new phases — create as work starts |

---

## History

| Date | Change |
| ---- | --------- |
| 2026-07-08 | Code audit 0.9.102: scorecard **code verified ~7.4/10**; retract wave-8 program ~9.8 claim; BL-189 published with evidence table |
| 2026-07-08 | Wave 8 program: BL artifacts shipped (0.9.102); pilots ready-for-field; subprocess executes in tests |
| 2026-07-08 | Wave 5–6: MES deploy script, historian SLA, MFA UI, Helm complete, scorecard ~9.2/10 |
| 2026-07-08 | Wave 4–5 commit: interop CI, Parquet, 218 symbols, marketplace install |
| 2026-07-08 | Wave 2–3 commit `d27f0be`: MFA, marketplace install, hard tenancy, mes-platform-production, 134→218 symbols path |
| 2026-07-07 | Wave 2 hardening: TOTP MFA, MQTT eventToVariable, analytics, BPMN messages, 30 agent scenarios, 991 tests green |
| 2026-07-07 | Wave 1 foundations: BL-140…190 skeletons (Partial) across Phase 25–32 |
| 2026-07-07 | Created Excellence Program Phase 25–32, BL-140…190, S31–S46 draft |
