> **Language:** Canonical English. Russian edition: [ru/reference-mes-walkthrough.md](../ru/reference-mes-walkthrough.md).

# Reference Walkthrough: Product Dispatch and Loading (`mes-reference`)

> **Status:** Lab — end-to-end reference bundle demonstrating shipment dispatch orders, tank level tracking, loading rack control, and overheat protection. Status tags: [doc-status.md](doc-status.md).

The **`mes-reference`** bundle ([`examples/mes-reference/`](../../examples/mes-reference/)) demonstrates how to connect application SQL tables (dispatch orders and tanks) with live equipment telemetry and alarm rules in the ISPF object tree without adding custom Java code to `ispf-server`.

---

## 1. Domain Model

| Entity | Representation in the Bundle | Purpose |
|--------|------------------------------|---------|
| **Dispatch Order** (`mes_dispatch_order`) | Table in SQL schema `app_mes_ref` | Order number (`DO-1001`, `DO-1002`), source tank, target volume, and status (`pending` → `filling` → `completed`) |
| **Storage Tank** (`mes_tank`) | Table in SQL schema `app_mes_ref` | Current tank fill percentage (demo seed: `T-01` at `72%`) |
| **Application Logic Hub** | `SINGLETON` node at `root.platform.singleton-blueprints.mes-reference-hub-v1` | Hosts the BFF script functions `mes_listOrders`, `mes_startFilling`, and `mes_completeFilling` |
| **Loading Rack & Sensor** | `DEVICE` nodes (`root.platform.devices.demo-sensor-01`, `mes-rack-01`) | Field telemetry (`temperature`, `meterLiters`, `flowRate`) and overheat alert rule |

---

## 2. Walkthrough Steps

1. **Deploy the bundle:** `POST /api/v1/applications/mes-reference/deploy` creates schema `app_mes_ref`, runs SQL migrations with two seed orders, registers the `SINGLETON` logic hub, and deploys the BFF functions.
2. **List dispatch orders:** call `mes_listOrders` on `root.platform.singleton-blueprints.mes-reference-hub-v1` via `POST /api/v1/bff/invoke`.
3. **Start loading:** call `mes_startFilling` with `orderNo = "DO-1001"` to transition the order status to `filling`.
4. **Complete loading:** call `mes_completeFilling` to record the loaded volume and transition the order to `completed`.
5. **Overheat protection:** when the loading rack temperature exceeds `85 °C`, the alert rule fires `mesRackOverTemp` and the correlator sets `alarmActive = true`.

---

## 3. Smoke Test Commands

Run the automated integration test:

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesReferenceBundleSmokeTest"
```

Deploy and invoke manually against a running local server:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-reference/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.singleton-blueprints.mes-reference-hub-v1","functionName":"mes_listOrders","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'
```

**Related guides:** [Solution developer guide](solution-developer-guide.md) · [Solution developer public API](solution-developer-public-api.md) · [Application solutions (`applications.md`)](applications.md).
