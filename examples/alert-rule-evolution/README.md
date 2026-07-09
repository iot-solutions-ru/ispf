# Alert rule evolution examples (ADR-0039)

Extended **`alert-rule-v1`** / `POST /api/v1/alert-rules` payloads. Same `ALERT` node type as today — no `alarm-v2` blueprint.

| File | Scenario |
|------|----------|
| [temperature-high.json](temperature-high.json) | Activate > 80 °C, deactivate < 70 °C, ack, clear event |

Fields marked in ADR phases B–F are **not all implemented** in `ispf-server` yet. For production today use existing alert rule fields only ([AUTOMATION.md](../../docs/en/automation.md)).

Spec: [docs/en/decisions/0039-unified-alarm-architecture.md](../../docs/en/decisions/0039-unified-alarm-architecture.md).
