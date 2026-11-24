> **Language:** Canonical English. Russian edition: [ru/mes-plan-interfaces.md](../ru/mes-plan-interfaces.md).

# MES ↔ SPP interface map (lab)

> **Status:** Lab — maps TZ IF-01…12 onto `mes-platform` BFF and outbox/inbox. Live CUTE, CIP, AVEVA, Kafka, and the corporate bus are **not** wired here. Status tags: [doc-status.md](doc-status.md).

Acceptance map: [mes-plan-execution.md](mes-plan-execution.md). Adapter notes for an IRS/.NET bridge: [examples/mes-platform/adapters/README.md](../../examples/mes-platform/adapters/README.md).

| IF | Direction | TZ intent (short) | Lab mapping in `mes-platform` | Live connector |
|---|---|---|---|---|
| IF-01 | MES → SPP | Master / product model slice | `mes_plan_if01_exportMaster` → `mes_erp_outbox` | Deferred |
| IF-02 | MES → SPP | Resource availability | `mes_plan_if02_exportResources` → outbox | Deferred |
| IF-03 | MES → SPP | Published plan + fact | `mes_plan_if03_exportPlanFact` → outbox | Deferred |
| IF-04 | SPP → MES | Plan candidate | `mes_plan_ingestCandidate` + `mes_exec_inbox` | Deferred |
| IF-05 | MES → SPP | Publish / republish ack | Written on `mes_plan_publish` / `mes_plan_republish` | Deferred |
| IF-06 | MES ↔ SPP | Reserve exchange | `mes_plan_reserveSoft` / `mes_plan_releaseReserve` / `mes_plan_listResources` | Deferred |
| IF-07 | MES → SPP | Replan request | `mes_plan_requestReplan` → outbox `IF-07:` | Deferred |
| IF-08 | SPP → MES | Confirmed material availability | `mes_plan_if08_ingestMaterialAvail` → inbox + resource update | Deferred |
| IF-09 | MES → Trade/logistics | PUB readiness for trade | `mes_plan_if09_exportTrade` → outbox | Deferred |
| IF-10 | MES → SPP | Capacity / asset load | `mes_plan_if10_exportCapacity` → outbox | Deferred |
| IF-11 | MES → SPP / BI | Plan–fact KPI slice | `mes_plan_if11_exportKpi` → outbox | Deferred |
| IF-12 | SPP → BI | Scenario / demand analytics | Out of MES scope | N/A |

Inspect queues with `mes_plan_listInbox` and `mes_plan_listOutbox` (`entityType` empty = all). `mes_erp_pollOutbox` still marks rows `simulated` (BL-169).
