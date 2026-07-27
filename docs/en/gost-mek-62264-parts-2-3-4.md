# GOST R IEC 62264 / IEC 62264 — Parts 1–5 coverage in ERP-MES Core

Normative editions (2026-07 review):

| Part | National document | = IEC | Note |
|------|-------------------|-------|------|
| **1** | **GOST R IEC 62264-1—2014** | IEC 62264-1:2013 | Models and terminology |
| **2** | **GOST R IEC 62264-2—2016** | IEC 62264-2:2013 | Objects and attributes L4↔L3 |
| **3** | **GOST R IEC 62264-3—2012** | IEC 62264-3:2007 | MOM activity models |
| **4** | **PNST 172—2016 / IEC 62264-4—2016** | IEC 62264-4:2016 | L3 objects; **preliminary** national standard |
| **5** | IEC 62264-5 | IEC 62264-5 | B2M transactions |

Level 3 MOM: bundle `examples/erp-mes-core` (`emc_*`) **2.2.0 / M5**.

**Attribute coverage source of truth:**  
[`examples/erp-mes-core/uml-conformance/uml-catalog.json`](../../examples/erp-mes-core/uml-conformance/uml-catalog.json)  
(validator: `uml-conformance/validate_uml_catalog.py`).

At milestone **M5** there are no open `missing` statuses in the catalog.

| Milestone | Bundle | Scope |
|-----------|--------|--------|
| **M3** | **2.0.x** | UML Parts **2+4**; Part **3** 32 cells + BFF map |
| **M4** | **2.1.x** | Part **5** UML; ISO 22400 KPI; APS-lite |
| **M5** | **2.2.x** | GOST object completeness + GOST UI dashboards |
| Product line | apps | **`erp-mes-aps`**, **`erp-mes-cmms`**, pharma, B2MML/1C |

Russian twin: [gost-mek-62264-parts-2-3-4.md](../ru/gost-mek-62264-parts-2-3-4.md).

---

## Demostand — manual acceptance (UI only)

Stand: [mes.iot-solutions.ru](https://mes.iot-solutions.ru/) (`?mode=operator`, `admin`/`admin`).

**Rule:** scenario checks are **manual in the browser only**. No REST/MCP/API. On bugs: fix → redeploy → repeat the same UI step.

| # | Step | UI | Expect |
|---|------|-----|--------|
| 1 | GOST dashboard | Operator → **ГОСТ 62264** | RRN / Containers / Tools / Software / Ops Def / Work Cap / Alerts with seed rows |
| 2 | RRN edges | ГОСТ 62264 → RRN Edges | `WU-A01`→tool/container; site→software |
| 3 | Ack alert | Acknowledge form | WA-DEMO-001 → ACKNOWLEDGED |
| 4 | Ops Definition upsert | form on GOST dashboard | OK + row visible |
| 5 | Maintenance | **ТОиР** | MR/MWO demo + create→accept→complete |
| 6 | Quality | **Качество** | Defects + QA tests |
| 7 | Inventory | **Склад** | INV-DEMO-001 Submit/Accept |
| 8 | MOM matrix | **MOM 62264-3** | 32 COVERED cells |
| 9 | CMMS (optional) | **erp-mes-cmms** | Spares / PM depth |

---

## Part 2 highlights (2.2+)

Containers / Tools / Software (§5.6), Operations Definition, Operations Schedule — **covered** (tables + reports on GOST dashboard).

## Part 3

32/32 cells **covered**; Maintenance / Quality / Inventory have list+action UIs on demostand (plus CMMS/pharma for product-line depth).

## Part 4 (PNST 172)

Resource Relationship Network, Work Capability, Work Master Capability, Work Alert, Work KPI — **covered**.

## Normative note

RF Part 4 = **PNST 172—2016**, not GOST R. Not a demostand model gap.

## Bundle versions

| Version | Added |
|---|---|
| **2.0.0** | UML Parts 2+4 + Part 3 BFF map |
| **2.1.0** | Part 5 UML + KPI + APS-lite |
| **2.2.0** | M5 GOST gaps closed + GOST/Maintenance dashboards |
