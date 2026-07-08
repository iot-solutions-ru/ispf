# ISPF competitive scorecard (BL-189)

Public readiness matrix for Phase 25–32 Excellence Program. Updated each release; baseline from Phase 24 (July 2026).

Scale **1–10** vs best-in-class: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, mature context-tree IIoT platforms.

| # | Dimension | Baseline | Post wave 1 | Post wave 2 | Target | Phase / BL |
|---|-----------|:--------:|:-----------:|:-----------:|:------:|------------|
| 1 | Unified data model (object tree) | 9.0 | **9.2** | **9.3** | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust), [29](ROADMAP_PHASE25.md#phase-29--mes-platform), [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / mimics | 7.0 | **7.8** | **7.9** | **10** | [26](ROADMAP_PHASE25.md#phase-26--hmi-excellence) — BL-146…152 |
| 3 | OT/IT connectivity (drivers) | 6.0 | **6.8** | **6.9** | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust) — BL-140…145 |
| 4 | Historian / time-series | 7.0 | **7.5** | **7.6** | **10** | [28](ROADMAP_PHASE25.md#phase-28--historian-at-scale) — BL-159…163 |
| 5 | Automation / alarms | 7.5 | **8.0** | **8.1** | **10** | [27](ROADMAP_PHASE25.md#phase-27--enterprise-security), [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.0** | **7.1** | **10** | [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.2** | **6.4** | **10** | [29](ROADMAP_PHASE25.md#phase-29--mes-platform) — BL-164…170 |
| 8 | Low-code velocity | 8.0 | **8.5** | **8.6** | **10** | [26](ROADMAP_PHASE25.md#phase-26--hmi-excellence), [31](ROADMAP_PHASE25.md#phase-31--ai-autopilot) — BL-146…152, BL-177…180 |
| 9 | AI-assisted development | 9.0 | **9.4** | **9.6** | **10** | [31](ROADMAP_PHASE25.md#phase-31--ai-autopilot) — BL-177…182 |
| 10 | Security / RBAC / tenancy | 6.5 | **7.2** | **7.3** | **10** | [27](ROADMAP_PHASE25.md#phase-27--enterprise-security) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **8.3** | **8.6** | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust), [28](ROADMAP_PHASE25.md#phase-28--historian-at-scale), [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-144…145, BL-186…187 |
| 12 | Ecosystem / marketplace | 4.0 | **5.0** | **5.5** | **10** | [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **9.3** | **9.4** | **10** | [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.0** | **9.0** | **10** | maintain — Spring Boot 4, React 19, NATS, ClickHouse option |

**Overall product score (post wave 2): ~8.5/10** — wave 2 hardening landed across Phases 25–32; field soak + signed marketplace bundles remain.

## Definition of done (10/10 overall)

From [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md#definition-of-done--1010-overall):

- All **14 dimensions ≥9.5**, none ≤8
- Agent regression ≥95% green (BL-178)
- Marketplace GA checklist complete (BL-183)
- Competitive scorecard published and versioned per release (this document)

## Post wave 2 highlights (Jul 2026)

| Dimension | Δ (wave 1→2) | Evidence |
|-----------|:------------:|----------|
| AI-assisted development | +0.2 | Agent e2e deploy integration test, playbook tools hardened (BL-177) |
| Ecosystem / marketplace | +0.5 | Local marketplace install endpoint + manifest validation (BL-183) |
| Deploy / scale | +0.3 | Helm ConfigMap for application.yml (BL-186) |
| MES / ISA-95 | +0.2 | mes-platform bundle in agent e2e pipeline (BL-177) |
| Manager-of-managers | — | Federation hub peer selector in operator shell (BL-188) |

## Post wave 1 highlights (Jul 2026)

| Dimension | Δ | Evidence |
|-----------|:-:|----------|
| AI-assisted development | +0.4 | Deploy playbook tools, 30-scenario regression CI, `/agent/metrics/tools` (BL-177, BL-178, BL-181) |
| SCADA / HMI | +0.8 | Symbol pack, mimic editor, spreadsheet live refresh (BL-146…152 partial) |
| Ecosystem / marketplace | +1.0 | Local bundle listing API + manifest validation skeleton (BL-183) |
| Deploy / scale | +0.3 | Helm chart skeleton deployment/service templates (BL-186) |
| Security / RBAC | +0.7 | MFA, per-variable ACL, hard tenancy foundations (BL-153…158 partial) |

## Update process

1. After each release tag, review shipped BL items in [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md).
2. Adjust baseline column when acceptance criteria met (evidence: docs, tests, lab metrics).
3. Note delta in release notes under **Competitive scorecard**.
