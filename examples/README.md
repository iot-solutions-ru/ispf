# Examples

В `main` нет отраслевых app bundle — только платформенные демо (`demo-sensor`, `demo-alarm-handler`).

| Пример | Описание |
|--------|----------|
| [demo-app](demo-app/) | SQL-отчёты: bundle + operator manifest для `appId=demo` |
| [mini-tec](mini-tec/) | Эталон мини-ТЭЦ: bundle + auto-bootstrap на старте server |
| [mes-reference](mes-reference/) | MES walkthrough: наряд → резервуар → эстакада |
| [mes-oee-reference](mes-oee-reference/) | BL-121: OEE KPI reference (A×P×Q) |
| [mes-platform](mes-platform/) | BL-165/170: MES platform certification bundle + OEE BFF |
| [escalation-templates](escalation-templates/) | BL-123: BPMN ack-timeout + correlator templates |
| [warehouse-app](warehouse-app/) | Reference app #2 (dogfooding REQ-PF) |
| [lab-training](lab-training/) | Importable lab package (Phase 15) |
| [spreadsheet-demo](spreadsheet-demo/) | Эталон: объект с `sheetValues` + виджет spreadsheet |
| [mes-defect-demo](mes-defect-demo/) | MES defect routing demo (bundle + smoke test) |
| [mes-ogp-events](mes-ogp-events/) | UC-25 OGP event registration: wizard, journal, roll map, 1C outbox |
| [agent-metrics-dashboard](agent-metrics-dashboard/) | BL-181: AI tool metrics dashboard layout + BFF sketch |
| [historian-sla-dashboard](historian-sla-dashboard/) | BL-161: historian query SLA dashboard layout + BFF sketch |
| [certification](certification/) | BL-190: exam question bank stubs (JSON) |

Прикладные bundle разворачиваются через `POST /api/v1/applications/{appId}/deploy` из репозитория проекта.

См. [docs/en/applications.md](../docs/en/applications.md), [docs/en/reference-mes-ogp-events-walkthrough.md](../docs/en/reference-mes-ogp-events-walkthrough.md) и [docs/en/plugins.md](../docs/en/plugins.md).
