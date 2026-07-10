# ISA-95 catalog — mapping ISPF object tree (BL-124)

How ISPF platform objects map to **ANSI/ISA-95** (IEC 62264) equipment hierarchy and MES activities. Use this when designing apps, bundles, and documentation for manufacturing customers.

**See also:** [object-model](object-model.md), [reference-mes-oee-walkthrough](reference-mes-oee-walkthrough.md), [mes-oee-reference](../examples/mes-oee-reference/).

---

## ISA-95 levels → ISPF

| ISA-95 level | Name | ISPF construct | Example path |
|--------------|------|----------------|--------------|
| **Level 4** | Business planning & logistics | Reports, SQL apps, external ERP sync | `root.platform.reports.*`, app bundles with BFF + outbox |
| **Level 3** | Manufacturing operations (MES) | Workflows, work queue, OEE BFF, app schemas | `root.platform.workflows.*`, `mes_oee_*` functions, `app_mes_*` tables |
| **Level 2** | Supervisory control (SCADA/HMI) | Dashboards, mimics, alert rules, correlators | `root.platform.dashboards.*`, `root.platform.mimics.*`, `root.platform.alert-rules.*` |
| **Level 1** | Basic control | Devices, drivers, variables, bindings | `root.platform.devices.*`, driver runtime, MQTT/OPC/BACnet points |
| **Level 0** | Physical process | Represented indirectly via device instances & semantic tags | Device variables, Brick/Haystack metadata on objects |

ISPF does **not** enforce a fixed ISA-95 folder tree — mapping is **conventional** via naming, blueprints, and app bundles.

---

## Equipment hierarchy (physical model)

Recommended dot-path convention for MES/SCADA projects:

```
root.platform.instances.{site}
  └── .areas.{area}
        └── .lines.{line}
              └── .units.{unit}          ← work center / machine
                    └── devices.{tag}    ← Level 1 device object
```

| ISA-95 term | ISPF node type | Notes |
|-------------|----------------|-------|
| Enterprise / Site | `INSTANCES` or app root object | One site per tenant in multi-tenant setups (BL-125+) |
| Area | Child under site instance | e.g. `...instances.plant-a.areas.refining` |
| Production line | Child under area | Links to OEE shift rows (`line_code`) |
| Work unit / equipment | `DEVICE` or typed instance | Driver points, functions, mimic bindings |
| Material lot / order | App SQL schema + BFF | e.g. `mes-reference` work orders |

Example from **mes-oee-reference**: line code `LINE-A01` in SQL maps to a logical line; device hub `demo-sensor-01` hosts BFF functions (pattern for small demos).

---

## MES activities (Level 3)

| ISA-95 activity | ISPF mechanism |
|-----------------|----------------|
| Production scheduling | App SQL + Operator UI / reports |
| Dispatching | Workflow user tasks → work queue |
| Performance analysis (OEE) | BFF `mes_oee_getKpi`, dashboards |
| Maintenance / quality trace | Events + historian + app tables |
| Alarm / exception management | Alert rules → correlators → workflows |

Reference walkthroughs:

- OEE: [reference-mes-oee-walkthrough](reference-mes-oee-walkthrough.md)
- Escalation: [reference-escalation-templates](reference-escalation-templates.md)
- MES orders: [mes-reference](../examples/mes-reference/)

---

## Information model (variables & semantics)

| Concept | ISPF |
|---------|------|
| Process segment data | Device variables, historian |
| Equipment capability | Object functions + metadata |
| Personnel / role | `root.platform.security.users`, workflow `assigneeRole` |
| Material definition | App schema (bundle migrations) |
| Semantic tagging | Brick/Haystack panels on objects (BL-104) |

---

## Quick checklist for solution developers

1. **Site/area/line** — model in `instances` or app-specific hierarchy; keep device paths stable for bindings.
2. **Level 2 HMI** — one dashboard per operator role; mimics bind to Level 1 devices.
3. **Level 3 MES** — workflows for exceptions; BFF/SQL for KPI; correlators for repeated events.
4. **Level 4** — reports and export (CSV/PDF/1C outbox patterns in reference apps).
5. **Documentation** — cite this catalog in customer solution docs; extend with project-specific path table.

---

## Related standards

| Standard | ISPF support |
|----------|--------------|
| ISA-95 / IEC 62264 | This catalog (conceptual mapping) |
| ISA-88 (batch) | Partial — workflows + app state; no batch engine |
| Brick / Haystack | Semantic metadata, query API (BL-101–105) |
| BPMN 2.0 | Subset in `ispf-plugin-workflow` |
