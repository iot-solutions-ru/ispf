> **Язык:** русская версия (вычитка). Канонический английский: [en/operator-apps.md](../en/operator-apps.md).

# Operator Apps

> **Статус:** Stable — Operator shell configuration. Хаб: [doc-status.md](doc-status.md).

Операторские приложения — конфиги **dashboard-UI** (`operator_app_ui`), указывающие на объекты `DASHBOARD` в дереве. Legacy `*.manifest.json` устарел.

## URL

| App | URL |
|-----|-----|
| Launcher | `/?mode=operator` |
| Alarm Console | `/?mode=operator&app=alarm-console` |
| Work Queue | `/?mode=operator&app=work-queue` |
| HMI Wall | `/?mode=operator&app=hmi-wall` |
| Platform HMI (fixtures) | `/?mode=operator&app=platform` |

Дерево: `root.platform.operator-apps.{appId}`.

## Стартовые шаблоны (Wave 4)

| appId | Dashboard | Назначение |
|-------|-----------|------------|
| `alarm-console` | `root.platform.dashboards.alarm-console` | Лента событий + alarm bar (`thresholdExceeded` / ack) |
| `work-queue` | `root.platform.dashboards.work-queue` | Full-bleed виджет work-queue |
| `hmi-wall` | `root.platform.dashboards.hmi-wall` | Хост мозаики `video-wall-2x2` |

- **Fixtures on:** сидируются при старте вместе с Platform HMI.
- **Clean install:** Operator launcher → **Install starter templates**, или `POST /api/v1/operator-apps/starters/install` (admin).

## API

| Method | Path |
|--------|------|
| `GET` | `/api/v1/operator-apps` |
| `GET` | `/api/v1/operator-apps/{appId}/ui` |
| `POST` | `/api/v1/operator-apps/{appId}` |
| `PUT` | `/api/v1/operator-apps/{appId}/ui` |
| `GET` | `/api/v1/operator-apps/starters` |
| `POST` | `/api/v1/operator-apps/starters/install` |

## Связанное

- [web-console](web-console.md) — operator vs admin shell
- [release-dogfood](release-dogfood.md) — pre-tag gate
