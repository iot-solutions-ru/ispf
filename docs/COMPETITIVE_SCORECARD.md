# ISPF competitive scorecard (BL-189)

Public readiness matrix for Phase 25–32 Excellence Program. Updated each release; baseline from Phase 24 (July 2026).

Scale **1–10** vs best-in-class: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, mature context-tree IIoT platforms.

| # | Dimension | Baseline | Target | Phase / BL |
|---|-----------|:--------:|:------:|------------|
| 1 | Unified data model (object tree) | 9.0 | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust), [29](ROADMAP_PHASE25.md#phase-29--mes-platform), [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / mimics | 7.0 | **10** | [26](ROADMAP_PHASE25.md#phase-26--hmi-excellence) — BL-146…152 |
| 3 | OT/IT connectivity (drivers) | 6.0 | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust) — BL-140…145 |
| 4 | Historian / time-series | 7.0 | **10** | [28](ROADMAP_PHASE25.md#phase-28--historian-at-scale) — BL-159…163 |
| 5 | Automation / alarms | 7.5 | **10** | [27](ROADMAP_PHASE25.md#phase-27--enterprise-security), [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **10** | [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **10** | [29](ROADMAP_PHASE25.md#phase-29--mes-platform) — BL-164…170 |
| 8 | Low-code velocity | 8.0 | **10** | [26](ROADMAP_PHASE25.md#phase-26--hmi-excellence), [31](ROADMAP_PHASE25.md#phase-31--ai-autopilot) — BL-146…152, BL-177…180 |
| 9 | AI-assisted development | 9.0 | **10** | [31](ROADMAP_PHASE25.md#phase-31--ai-autopilot) — BL-177…182 |
| 10 | Security / RBAC / tenancy | 6.5 | **10** | [27](ROADMAP_PHASE25.md#phase-27--enterprise-security) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust), [28](ROADMAP_PHASE25.md#phase-28--historian-at-scale), [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-144…145, BL-186…187 |
| 12 | Ecosystem / marketplace | 4.0 | **10** | [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **10** | [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **10** | maintain — Spring Boot 4, React 19, NATS, ClickHouse option |

## Definition of done (10/10 overall)

From [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md#definition-of-done--1010-overall):

- All **14 dimensions ≥9.5**, none ≤8
- Agent regression ≥95% green (BL-178)
- Marketplace GA checklist complete (BL-183)
- Competitive scorecard published and versioned per release (this document)

## Current snapshot (foundations only)

| Dimension | Score | Notes |
|-----------|:-----:|-------|
| Unified data model | 9.1 | +QUERY, +EVENT_FILTER object types (BL-173, BL-174) |
| AI-assisted development | 9.2 | E2e deploy playbook + regression skeleton (BL-177, BL-178) |
| Ecosystem / marketplace | 4.5 | GA checklist + demo listing manifest (BL-183) |
| Documentation / DX | 9.1 | Scorecard + agent regression docs (BL-189) |

*Remaining dimensions unchanged from Phase 24 baseline until respective BL items ship.*

## Update process

1. After each release tag, review shipped BL items in [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md).
2. Adjust baseline column when acceptance criteria met (evidence: docs, tests, lab metrics).
3. Note delta in release notes under **Competitive scorecard**.
