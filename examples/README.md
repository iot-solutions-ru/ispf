# Examples

В `main` нет отраслевых app bundle — только платформенные демо (`demo-sensor`, `demo-alarm-handler`).

| Пример | Описание |
|--------|----------|
| [demo-app](demo-app/) | SQL-отчёты: bundle + operator manifest для `appId=demo` |

Прикладные bundle разворачиваются через `POST /api/v1/applications/{appId}/deploy` из репозитория проекта.

См. [docs/APPLICATIONS.md](../docs/APPLICATIONS.md) и [docs/PLUGINS.md](../docs/PLUGINS.md).
