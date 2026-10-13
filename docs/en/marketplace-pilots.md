> **Language:** Canonical English. Russian edition: [ru/marketplace-pilots.md](../ru/marketplace-pilots.md).

# Marketplace / solution pilots (working references)

> **Status:** These catalog installs are **working / REAL** (CI `validate-catalog.mjs`, dogfood, operator UIs). They are **not** driver `FIELD` evidence — see [ADR-0067](decisions/0067-field-certified-maturity.md) §1b.

Industry and solution pilots ship under [`examples/marketplace-catalog/`](../../examples/marketplace-catalog/). Install via **System → Solutions → Marketplace** (local catalog / publish script) or the deploy API. See [marketplace](marketplace.md).

## Honesty lanes

| Lane | Meaning | Today |
|------|---------|-------|
| **This page** | Installable solution / SPA / workflow packs | Many **working** |
| Lab OT Trust (BL-140) | Protocol soaks on lab VLAN | Lab evidence; not customer plant |
| Driver FIELD / CERTIFIED | Named **plant** + soak + OT sign-off for a **protocol driver** | **Zero** matrix rows |

A green marketplace install does **not** promote Modbus/MQTT/OPC UA (or any driver) to FIELD.

## How “working” is proven

1. Listing + bundle (or ui-pack zip) present under `examples/marketplace-catalog/<slug>/`
2. CI job `marketplace-catalog` runs `node tools/marketplace-catalog/validate-catalog.mjs` on every PR
3. Operator / demostand dogfood for the packs listed as product references below

## Product reference pilots (by domain)

Grouped for tender / agent teaching — not an exhaustive tag dump. Companion `*-ui` rows are SPA ui-packs for the same product.

### Buildings / energy / OT SCADA

| Slug | Role |
|------|------|
| `building-hvac` | Building HVAC reference |
| `tank-farm` | Tank farm SCADA |
| `oil-control` (+ `oil-control-ui`) | Fuel / metrology control |
| `mini-tec` | Mini TEC telemetry / twin |
| `predictive` (+ `predictive-ui`) | Predictive analytics OT |
| `ot-alarm-classify` | OT alarm classify workflow template |
| `mqtt-temperature` | MQTT temperature lab |

### MES / ISA-95 / manufacturing

| Slug | Role |
|------|------|
| `mes-platform` / `mes-platform-production` | MES product + production profile |
| `mes-reference` / `mes-oee-reference` | MES / OEE references |
| `mes-cto` | MES CTO configurator |
| `mes-integration-catalog` | MES integration catalog (B2MML / 1C patterns) |
| `mes-ogp-events` | OGP event registration |
| `erp-mes-core` / `erp-mes-aps` / `erp-mes-cmms` / `erp-mes-pharma` / `erp-mes-printing` | ERP–MES pattern packs |
| `laminate-flow-mes` (+ ui) | Laminate flow MES |
| `mes-wrangel-cps` (+ ui) / `woodmes` (+ ui) | Plant CPS / wood MES demos |

### Digital twins / retail / logistics

| Slug | Role |
|------|------|
| `farmtwin` (+ ui) | Farm twin |
| `storetwin` (+ ui) | Store twin |
| `warehousetwin` (+ ui) / `warehouse-reference` | Warehouse twin / reference |

### IT / training / demos

| Slug | Role |
|------|------|
| `it-infra-monitoring` (+ `m11-monitor-ui`) | IT infra / NMS monitoring |
| `lab-training` | Lab training / ACL |
| `demo-app` / `marketplace-demo` / `spreadsheet-demo` / `simulator-profiles` | Training & platform demos |
| `claims-control` / `coffegate` (+ uis) / `doom` | Additional app / SPA demos |

Full folder list: `examples/marketplace-catalog/` (application + ui-pack + workflow-template listings).

## What this does **not** claim

- Customer **plant** FIELD for any protocol driver ([ADR-0067](decisions/0067-field-certified-maturity.md))
- Live ERP 1C/SAP round-trip (**P-ERP** / BL-169 parked) — MES packs may mark ERP stubs `simulated`
- Partner Portal SaaS sync (directory is in-server; portal remains external)

## Related

- [marketplace](marketplace.md) — browse / install / CI gate
- [competitive-scorecard](competitive-scorecard.md) — dim 12 ecosystem; dim 3 OT drivers
- [0067-field-certified-maturity](decisions/0067-field-certified-maturity.md) — driver FIELD vs this lane
- [field-pilot-playbook](field-pilot-playbook.md) — ready-for-field plant gate
