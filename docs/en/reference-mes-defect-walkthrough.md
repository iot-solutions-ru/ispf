> **Language:** Canonical English. Russian edition: [ru/reference-mes-defect-walkthrough.md](../ru/reference-mes-defect-walkthrough.md).

# Reference Walkthrough: Scrap Routing and Disposition (`mes-defect-demo`)

> **Status:** Lab — end-to-end example combining automated scrap route calculation with dispatcher confirmation in the BPMN Work Queue. Status tags: [doc-status.md](doc-status.md).

The **`mes-defect-demo`** bundle ([`examples/mes-defect-demo/`](../../examples/mes-defect-demo/)) demonstrates how a production scrap event from a line is linked to an active work order, evaluated for a recommended rework/transport route, and confirmed by a dispatcher via a BPMN workflow (`root.platform.workflows.mes-defect-distribution`).

---

## 1. Domain Entities and Routing Rules

| Table / Node | Purpose |
|--------------|---------|
| `mes_line` | Production lines of types `A`, `B`, and `D` (`LINE-A01`, `LINE-B01`, `LINE-D01`) |
| `mes_order` | Production order with status (`open`, `closing`, `closed`) and accumulated scrap |
| `mes_defect_event` | Recorded scrap volume and special-scrap flag (`isSpecialScrap`) |
| `mes_recommendation` | Recommended disposition route: `REWORK_A`, `FEED_QUEUE`, `TRANSPORT_HUB`, or `SPECIAL_ROUTE` |
| **Application Logic Hub** | `SINGLETON` at `root.platform.singleton-blueprints.mes-defect-demo-hub-v1` (hosts BFF functions `mes_listLines`, `mes_simulateDefect`, `mes_calculateRoute`, `mes_confirmRoute`, `mes_finalizeDefect`) |
| **BPMN Workflow** | `root.platform.workflows.mes-defect-distribution` |

---

## 2. Walkthrough Steps

1. **Deploy the bundle:** `POST /api/v1/applications/mes-defect-demo/deploy` creates the schema, functions, BPMN workflow, and operator dashboards.
2. **Simulate a scrap event:** on dashboard `mes-defect-simulator`, invoke `mes_simulateDefect` to insert a new defect event.
3. **Run the BPMN disposition workflow:** the workflow calls `mes_calculateRoute`, writes a route recommendation into `mes_recommendation`, and places a confirmation task into the operator **Work Queue**.
4. **Dispatcher confirmation:** open `?mode=operator&app=mes-defect-demo`, review and confirm the route (`mes_confirmRoute`), and complete the task.
5. **Finalization:** the workflow executes `mes_finalizeDefect` and publishes `mesDefectRouted`.

Run the automated test:

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesDefectDemoBundleSmokeTest"
```

**Related guides:** [Workflows & BPMN (`workflows.md`)](workflows.md) · [Application solutions (`applications.md`)](applications.md).
