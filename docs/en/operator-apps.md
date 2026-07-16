> **Language:** Canonical English. Russian edition: [ru/operator-apps.md](../ru/operator-apps.md).

# Operator Apps

Operator applications are **dashboard-UI** configs (`operator_app_ui`) pointing at `DASHBOARD` objects on the tree. Legacy `*.manifest.json` is deprecated.

## URLs

| App | URL |
|-----|-----|
| Launcher | `/?mode=operator` |
| Alarm Console | `/?mode=operator&app=alarm-console` |
| Work Queue | `/?mode=operator&app=work-queue` |
| HMI Wall | `/?mode=operator&app=hmi-wall` |
| Platform HMI (fixtures) | `/?mode=operator&app=platform` |

Tree: `root.platform.operator-apps.{appId}`.

## Starter templates (Wave 4)

| appId | Dashboard | Purpose |
|-------|-----------|---------|
| `alarm-console` | `root.platform.dashboards.alarm-console` | Event feed + alarm bar (`thresholdExceeded` / ack) |
| `work-queue` | `root.platform.dashboards.work-queue` | Full-bleed work-queue widget |
| `hmi-wall` | `root.platform.dashboards.hmi-wall` | `video-wall-2x2` mosaic host |

- **Fixtures on:** seeded at startup with Platform HMI.
- **Clean install:** Operator launcher → **Install starter templates**, or `POST /api/v1/operator-apps/starters/install` (admin).

## API

| Method | Path |
|--------|------|
| `GET` | `/api/v1/operator-apps` |
| `GET` | `/api/v1/operator-apps/{appId}/ui` |
| `POST` | `/api/v1/operator-apps/{appId}` |
| `PUT` | `/api/v1/operator-apps/{appId}/ui` |
| `GET` | `/api/v1/operator-apps/starters` |
| `POST` | `/api/v1/operator-apps/starters/install` |

## Related

- [web-console](web-console.md) — operator vs admin shell
- [release-dogfood](release-dogfood.md) — pre-tag gate
