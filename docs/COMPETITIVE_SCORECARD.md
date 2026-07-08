# ISPF competitive scorecard (BL-189)

Public readiness matrix for Phase 25–32 Excellence Program. Updated each release; baseline from Phase 24 (July 2026).

Scale **1–10** vs best-in-class: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, mature context-tree IIoT platforms.

| # | Dimension | Baseline | Post wave 1 | Post wave 2 | Post wave 3 | Post wave 4 | Post wave 5 | Post wave 6 | Target | Phase / BL |
|---|-----------|:--------:|:-----------:|:-----------:|:-----------:|:-----------:|:-----------:|:-----------:|:------:|------------|
| 1 | Unified data model (object tree) | 9.0 | **9.2** | **9.3** | **9.4** | **9.5** | **9.6** | **9.7** | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust), [29](ROADMAP_PHASE25.md#phase-29--mes-platform), [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / mimics | 7.0 | **7.8** | **7.9** | **8.0** | **8.5** | **8.6** | **9.0** | **10** | [26](ROADMAP_PHASE25.md#phase-26--hmi-excellence) — BL-146…152 |
| 3 | OT/IT connectivity (drivers) | 6.0 | **6.8** | **6.9** | **7.0** | **8.5** | **8.7** | **9.3** | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust) — BL-140…145 |
| 4 | Historian / time-series | 7.0 | **7.5** | **7.6** | **7.7** | **8.5** | **8.6** | **9.0** | **10** | [28](ROADMAP_PHASE25.md#phase-28--historian-at-scale) — BL-159…163 |
| 5 | Automation / alarms | 7.5 | **8.0** | **8.1** | **8.2** | **8.6** | **8.7** | **9.1** | **10** | [27](ROADMAP_PHASE25.md#phase-27--enterprise-security), [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.0** | **7.1** | **7.2** | **8.5** | **8.6** | **9.0** | **10** | [30](ROADMAP_PHASE25.md#phase-30--automation-depth) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.2** | **6.4** | **6.6** | **8.5** | **8.8** | **9.5** | **10** | [29](ROADMAP_PHASE25.md#phase-29--mes-platform) — BL-164…170 |
| 8 | Low-code velocity | 8.0 | **8.5** | **8.6** | **8.7** | **8.8** | **9.0** | **9.5** | **10** | [26](ROADMAP_PHASE25.md#phase-26--hmi-excellence), [31](ROADMAP_PHASE25.md#phase-31--ai-autopilot) — BL-146…152, BL-177…180 |
| 9 | AI-assisted development | 9.0 | **9.4** | **9.6** | **9.7** | **9.8** | **9.85** | **9.95** | **10** | [31](ROADMAP_PHASE25.md#phase-31--ai-autopilot) — BL-177…182 |
| 10 | Security / RBAC / tenancy | 6.5 | **7.2** | **7.3** | **7.4** | **8.5** | **8.8** | **9.3** | **10** | [27](ROADMAP_PHASE25.md#phase-27--enterprise-security) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **8.3** | **8.6** | **9.0** | **9.2** | **9.3** | **9.5** | **10** | [25](ROADMAP_PHASE25.md#phase-25--ot-trust), [28](ROADMAP_PHASE25.md#phase-28--historian-at-scale), [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-144…145, BL-186…187 |
| 12 | Ecosystem / marketplace | 4.0 | **5.0** | **5.5** | **6.0** | **8.5** | **8.7** | **9.0** | **10** | [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **9.3** | **9.4** | **9.5** | **9.6** | **9.7** | **9.8** | **10** | [32](ROADMAP_PHASE25.md#phase-32--ecosystem--market) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.0** | **9.0** | **9.0** | **9.0** | **9.0** | **9.0** | **10** | maintain — Spring Boot 4, React 19, NATS, ClickHouse option |

**Overall product score (post wave 6): ~9.5/10** — wave 6 landed agent-regression CI gate (BL-178), live LLM mes-platform deploy smoke (BL-177), field pilot sign-off template (BL-140), MFA admin panel, event-filter inspector, federation peer health poll, and scorecard wave 6 refresh.

## Definition of done (10/10 overall)

From [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md#definition-of-done--1010-overall):

- All **14 dimensions ≥9.5**, none ≤8
- Agent regression ≥95% green (BL-178)
- Marketplace GA checklist complete (BL-183)
- Competitive scorecard published and versioned per release (this document)

## Post wave 6 highlights (Jul 2026)

| Dimension | Δ (wave 5→6) | Evidence |
|-----------|:------------:|----------|
| MES / ISA-95 | +0.7 | `AgentLiveDeploySmokeTest` live LLM deploy, mes-platform cert scenarios (BL-177) |
| AI-assisted development | +0.1 | Dedicated `agent-regression` CI job, 40-scenario schema gate (BL-178) |
| OT/IT connectivity | +0.6 | Field pilot playbook sign-off template, lab matrix complete (BL-140) |
| Security / RBAC | +0.5 | MFA admin panel (`SecurityMfaPanel`), TOTP enrollment API (BL-153) |
| Low-code velocity | +0.5 | Event filter inspector, automation depth wave 6 (BL-174) |
| SCADA / HMI | +0.4 | Federation peer health badge in operator shell (BL-188) |
| Documentation / DX | +0.1 | Scorecard wave 6, field pilot close-out template (BL-189, BL-140) |

## Post wave 5 highlights (Jul 2026)

| Dimension | Δ (wave 4→5) | Evidence |
|-----------|:------------:|----------|
| MES / ISA-95 | +0.3 | mes-platform-production bundle + batch/SPC integration tests (BL-170) |
| Security / RBAC | +0.3 | MFA foundations, per-variable ACL hardening (BL-153…154) |
| OT/IT connectivity | +0.2 | Driver interop nightly, field pilot dry-run scripts (BL-141) |
| AI-assisted development | +0.05 | Agent e2e deploy integration test without LLM (BL-177) |
| Deploy / scale | +0.1 | Helm values refinement, federation health poll stub (BL-186, BL-188) |

## Post wave 4 highlights (Jul 2026)

| Dimension | Δ (wave 3→4) | Evidence |
|-----------|:------------:|----------|
| OT/IT connectivity | +1.5 | 20 PRODUCTION drivers, interop CI smoke, [FIELD_PILOT_PLAYBOOK.md](FIELD_PILOT_PLAYBOOK.md), OPC UA server write-back (BL-140, BL-141, BL-143) |
| SCADA / HMI | +0.5 | 218-symbol P&ID pack v2 (BL-146) |
| Historian | +0.8 | Parquet bulk export REST (BL-163) |
| AI-assisted development | +0.1 | 40 agent regression scenarios (BL-178) |
| Ecosystem / marketplace | +2.5 | Partner program tiers API (BL-184) |
| MES / ISA-95 | +1.9 | mes-platform agent scenarios + reference bundle |
| Documentation / DX | +0.1 | Scorecard wave 4, [CERTIFICATION.md](CERTIFICATION.md), field pilot playbook (BL-189, BL-190) |
| Deploy / scale | +0.2 | Federation peer health poll (BL-188) |

## Post wave 3 highlights (Jul 2026)

| Dimension | Δ (wave 2→3) | Evidence |
|-----------|:------------:|----------|
| Deploy / scale | +0.4 | Helm ConfigMap for application.yml (BL-186) |
| Ecosystem / marketplace | +0.5 | Local install + uninstall endpoints, manifest validation (BL-183) |
| AI-assisted development | +0.1 | Agent e2e deploy integration test on mes-platform (BL-177) |
| SCADA / HMI | +0.1 | Federation hub peer selector in operator shell (BL-188) |
| MES / ISA-95 | +0.2 | mes-platform bundle in agent e2e pipeline (BL-177) |
| Documentation / DX | +0.1 | Context pack competitive gap index from scorecard (BL-182) |

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
