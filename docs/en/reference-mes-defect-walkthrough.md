> **Language:** Canonical English. Russian edition: [ru/reference-mes-defect-walkthrough.md](../ru/reference-mes-defect-walkthrough.md).

# MES defect distribution walkthrough

End-to-end reference scenario: **line scrap event → order binding → route forecast → dispatcher confirm → MES record**.

All data is **fictional** (demo lines, orders `DEMO-*`, neutral route codes).

Artifacts: [examples/mes-defect-demo/](../examples/mes-defect-demo/), bundle `appId` = `mes-defect-demo`.

Reference BPMN (drawio): [docs/assets/mes-defect-bpmn.drawio.xml](assets/mes-defect-bpmn.drawio.xml).

## Domain (simplified MES)

| Entity | Description |
|----------|----------|
| **Line** (`mes_line`) | Type A/B/D, demo node loads |
| **Order** (`mes_order`) | Mock ERP: status `open` / `closing` / `closed`, accumulated scrap |
| **Scrap event** (`mes_defect_event`) | Volume, special-type flag, transitional remainder |
| **Recommendation** (`mes_recommendation`) | Route: `REWORK_A` / `FEED_B` / `TRANSPORT_HUB` / `SPECIAL_ROUTE` |
| **MES Hub** | `root.platform.devices.mes-hub-01` — BFF, bindings, event journal |
| **Workflow** | `root.platform.workflows.mes-defect-distribution` |

## Line types

| Type | Line | Forecast (demo) |
|-----|-------|----------------|
| A | LINE-A01 | Rework on line or transfer to adjacent |
| B | LINE-B01 | Feed or queue |
| D | LINE-D01 | Transport to hub; special type → alternate route |

## Scenario steps

| # | Action | API / UI | Effect |
|---|----------|----------|--------|
| 1 | Deploy bundle | `POST /api/v1/applications/mes-defect-demo/deploy` | schema, functions, workflow, dashboards |
| 2 | List lines | BFF `mes_listLines` @ `mes-hub-01` | 3 demo lines |
| 3 | SCADA simulation | Dashboard `mes-defect-simulator` → `mes_simulateDefect` | INSERT event |
| 4 | Start BPMN | Run distribution button | workflow to user task |
| 5 | Forecast | `mes_calculateRoute` in BPMN | INSERT `mes_recommendation` |
| 6 | Confirm | Work queue Operator App `mes-defect-demo` | `mes_confirmRoute` |
| 7 | Finalize | `mes_finalizeDefect` + `mesDefectRouted` | status `routed` |

## Demo cases

1. **LINE-A01**, 12 kg, `active` → `REWORK_A` → work-queue task.
2. **LINE-A01**, `orderScenario=closing`, volume > 10 kg → transitional remainder.
3. **LINE-B01**, 8 kg → `FEED_QUEUE`.
4. **LINE-D01**, normal scrap → `TRANSPORT_HUB`.
5. **LINE-D01**, `isSpecialScrap=1` → `SPECIAL_ROUTE`.

## CI

`MesDefectDemoBundleSmokeTest` — deploy, list lines, simulate + workflow run + work-queue, special scrap → `SPECIAL_ROUTE`.

## Related documents

- [workflows](workflows.md) — BPMN on ISPF
- [applications](applications.md) — bundle deploy
