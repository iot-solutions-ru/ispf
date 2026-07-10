> **Language:** Canonical English. Russian edition: [ru/reference-mes-walkthrough.md](../ru/reference-mes-walkthrough.md).

# MES reference walkthrough

End-to-end reference scenario for ISPF solution developers: **dispatch order → tank → loading rack → completion**. No custom Java in `ispf-server`.

Artifacts: [examples/mes-reference/](../examples/mes-reference/), bundle `appId` = `mes-reference`.

## Domain (simplified MES)

| Entity | Description |
|----------|----------|
| **Dispatch order** (`mes_dispatch_order`) | Shipment order: number, tank, volume, status |
| **Tank** (`mes_tank`) | Fill level (demo: `T-01`, 72%) |
| **Loading rack** | Object path `root.platform.devices.demo-sensor-01` — BFF functions and event journal |
| **Rack device** | `root.platform.devices.mes-rack-01` — created from `objects[]` in bundle |

Order statuses: `pending` → `filling` → `completed`.

## Scenario steps

| # | Action | Object path / API | Event / effect | Operator |
|---|----------|-------------------|------------------|----------|
| 1 | Deploy bundle | `POST /api/v1/applications/mes-reference/deploy` | schema `app_mes_ref`, migrations, functions | Admin |
| 2 | List orders | BFF `mes_listOrders` @ `demo-sensor-01` | SQL read | Operator UI journal path |
| 3 | Start filling | BFF `mes_startFilling` + `orderNo` | status → `filling` | Form button (future dashboard) |
| 4 | Virtual meter | `virtual` driver profile `meter` + `filling=true` | `meterLiters`, `flowRate` | See [MesPlatformApiTest](../packages/ispf-server/src/test/java/com/ispf/server/mes/MesPlatformApiTest.java) |
| 5 | Complete | BFF `mes_completeFilling` | status → `completed` | Operator confirm |
| 6 | Rack overheat | alert `mesRackOverTemp` when `temperature > 85` | correlator → `alarmActive=true` | Alarm panel |
| 7 | *(optional)* BPMN workflow | `workflows[]` in bundle → deploy | side-effect via `publish_nats` or `fire_event` | Admin / automation |

## REQ-PF mapping

| Mechanism | Usage in MES reference |
|----------|-------------------------------|
| **PF-01** bundle deploy | `migrations`, `functions`, `operatorUi` |
| **PF-05** objects[] | `mes-rack-01` device |
| **PF-02** BFF invoke | `mes_listOrders`, `mes_startFilling`, `mes_completeFilling` |
| **Correlators** | `mesRackOverTemp` → SET_VARIABLE |
| **Alert rules** | temperature guard on loading rack |
| **PF-09** virtual driver | optional step 4 (meter profile) |

## Readiness criteria

- [x] Bundle deploys on `test` profile without custom Java
- [x] `mes_listOrders` returns 2 seed orders
- [x] Lifecycle `startFilling` → `completeFilling` for `DO-1001`
- [x] CI: `MesReferenceBundleSmokeTest`

## Smoke commands

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesReferenceBundleSmokeTest"
```

Locally (server running):

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-reference/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_listOrders","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'
```

## Related documents

- [solution-developer-guide](solution-developer-guide.md)
- [solution-developer-public-api](solution-developer-public-api.md)
- [applications](applications.md)
