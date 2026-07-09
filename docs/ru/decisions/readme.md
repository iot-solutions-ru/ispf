# Архитектурные решения (ADR)

Вычитанные русские версии ADR в этой папке. Канонический английский: [../../en/decisions/](../../en/decisions/).

| Диапазон | Темы |
|----------|------|
| 0001–0008 | Граница platform/solution, dogfooding, лицензии, AI gates, agent, MCP, bundle, federation |
| 0009–0016 | TimescaleDB, bindings, model types, UI groups, i18n, automation, event history, AGPL, ClickHouse |
| 0017–0024 | Telemetry ingest, fixtures/CEL, rules, timezones, Haystack, drivers matrix, pub/sub |
| 0025–0037 | Cassandra/Scylla, elastic ingress, event journal, cluster HA, replica sync, historian dual-write, portability |
| 0038–0041 | Analytics platform, unified alarms, Computations UI, historian binding rules |

См. также [../../en/decisions/readme.md](../../en/decisions/readme.md) (индекс на английском).

| ID | Тема |
|----|------|
| [0039](0039-unified-alarm-architecture.md) | Эволюция alert rule (тот же `alert-rule-v1`) |
| [0040](0040-unified-computations-ui.md) | Единая вкладка «Вычисления» |
| [0041](0041-multi-tag-historian-computations.md) | Historian binding rules (мультитег) |
