> **Language:** Canonical English. Russian edition: [ru/reference-mini-tec-walkthrough.md](../ru/reference-mini-tec-walkthrough.md).

# Mini-TEC reference walkthrough

End-to-end **optional demo** of ISPF for a **mini-CHP plant control system**: 3× gas piston units (GPU), gas pressure reduction block (GRPB), 10/0.4 kV switchgear (RUMB), diesel generator (DGU), load module, station hub, protections, operator HMI with single-line diagram. No custom Java in `ispf-server` (bootstrap + bundle). **Not part of platform roadmap** — see [roadmap.md § Phase 18](roadmap.md).

Artifacts: [examples/mini-tec/](../examples/mini-tec/), `appId` = `mini-tec`.

See also [examples/mini-tec/README.md](readme.md), agent playbook `miniTecReference()` in `AgentPlaybooks.java`.

## Domain

| Entity | Object path | Model |
|----------|-------------|--------|
| **GPU 1–3** | `root.platform.devices.mini-tec-plant.gpu-0N` | `mini-tec-gpu-v1` |
| **GRPB** | `...grpb` | `mini-tec-grpb-v1` |
| **RUMB 10/0.4 kV** | `...rumb-10kv` | `mini-tec-rumb-v1` |
| **DGU** | `...dgu` | `mini-tec-dgu-v1` |
| **Load module** | `...load-module` | `mini-tec-load-module-v1` |
| **Station hub** | `...station-hub` | `mini-tec-station-hub-v1` |

Installed capacity: **4440 kW** (3×1480). Virtual driver profiles: `tec-gpu`, `tec-grpb`, `tec-rumb`, `tec-dgu`, `tec-load`.

## Scenario steps

| # | Action | API / UI | Effect |
|---|----------|----------|--------|
| 1 | Start server | `MiniTecPlatformBootstrap` | Models, objects, dashboards, workflows, automation, operator UI |
| 2 | Operator HMI | `?mode=operator&app=mini-tec` | **Integrated mimic** `mini-tec-hmi` (default) |
| 3 | Diagram zones | tabs Generation / Gas / Electrical supply | Mimics `mini-tec-single-line`, `mini-tec-zone-gas`, `mini-tec-zone-electrical` |
| 4 | Node card | click block on diagram (`setSelection`) | Right panel: status, P, sparkline, chart |
| 5 | Station summary | dashboard `mini-tec-overview` | Hub + GPU KPI |
| 6 | Training fire | `simulate_fire` on GRPB | Alarm bar, gas trip of **all** GPUs, email/webhook (if relay configured) |
| 7 | Underpower | load > generation | correlator → `mini-tec-load-module-auto-unload` |
| 8 | KPI / trends | `mini-tec-kpi`, `mini-tec-trends` | OEE/MTBF/MTTR, historian charts |
| 9 | Daily journal | schedule `mini-tec-daily-journal-etl` | `aggregate_daily_journal` → `tec_daily_journal` |
| 10 | Redeploy bundle | `POST /api/v1/applications/mini-tec/deploy` | Idempotent sync from `examples/mini-tec/bundle.json` |

## Dashboards

| Path | Purpose |
|------|------------|
| `root.platform.dashboards.mini-tec-hmi` | **Operator mimic** (default) |
| `root.platform.dashboards.mini-tec-overview` | Station summary |
| `root.platform.dashboards.mini-tec-single-line` | Single-line diagram (SLD widget) |
| `root.platform.dashboards.mini-tec-kpi` | KPI (OEE, MTBF, MTTR) |
| `root.platform.dashboards.mini-tec-trends` | Trend comparison |
| `root.platform.dashboards.mini-tec-gpu-detail` | GPU — detail |
| `root.platform.dashboards.mini-tec-grpb` | GRPB (+ training simulations) |
| `root.platform.dashboards.mini-tec-rumb` | RUMB |
| `root.platform.dashboards.mini-tec-dgu` | DGU |
| `root.platform.dashboards.mini-tec-load-module` | Load module |
| `root.platform.dashboards.mini-tec-protections` | Protections |
| `root.platform.dashboards.mini-tec-exploitation` | Operations (all 3 GPUs) |

## REQ-PF / mechanism mapping

| Mechanism | Usage |
|----------|---------------|
| **Models** | 6 INSTANCE models + binding rules on hub |
| **Virtual driver** | Profiles `tec-*`, poll in `VirtualTecPoll.java` |
| **Binding rules** | Cross-object aggregates on `station-hub` (0010) |
| **Workflow** | 5 BPMN: gas trip, load unload, GPU start, ack protection, shift handover |
| **Automation** | Alert rules + correlators on protections |
| **PF-02** | App SQL: `tec_daily_journal`, `tec_consumer_load` |
| **Operator UI** | `root.platform.operator-apps` → mini-tec menus |

## Acceptance (smoke checklist)

| # | Check | Expected |
|---|----------|----------|
| 1 | `?mode=operator&app=mini-tec` | Integrated HMI `mini-tec-hmi`, alarm bar on top |
| 2 | Click GPU-2 on diagram | Right card: P, status, sparkline 1h, start/stop |
| 3 | GRPB → Training fire | Red highlight, sound, gas trip of **all** GPUs |
| 4 | Load > generation | `stationUnderpower`, auto shed, event in event-feed |
| 5 | Login `operator-gas` | `GET` variables `rumb-10kv` → 403; `grpb` → OK |
| 6 | PNG export on mimic; CSV trend P for 24h | Files saved |

## RBAC and shift

- `operator-gas` — ACL OWNER on `grpb`; `operator-electrical` — on `rumb-10kv`, `load-module`.
- `operator-engineer` — full access to station devices (no object ACL on GPU/hub).
- Workflow `mini-tec-shift-handover` — user task to confirm active alarms + `aggregate_daily_journal`.

## REST / WebSocket / OPC-UA

See [examples/mini-tec/README.md](readme.md). OPC-UA lab: one GPU can be switched to `opcua` driver profile instead of `virtual` ([drivers](drivers.md)).

## NFR (production)

For industrial CHP mimic operation see [deployment.md § SCADA NFR](deployment.md).
