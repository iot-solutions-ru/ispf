# Rolling average analytics template (BL-160)

Walkthrough for **asset analytics AF-lite**: catalog template → device derived tag → dashboard chart.

## Prerequisites

- ISPF server with historian enabled (`variable_samples` writes on `temperature` or your source tag).
- Demo device `root.platform.devices.demo-sensor-01` with `temperature` (`historyEnabled=true`).

## 1. Open the analytics catalog

In **Explorer**, select `root.platform.analytics`. Built-in templates:

| templateId | helper | blueprint | default bucket |
| --- | --- | --- | --- |
| `rollingAvg` | `rollingAvg` | `rolling-avg-v1` | `5m` |
| `rateOfChange` | `rateOfChange` | `rate-of-change-v1` | `1h` |
| `oee` | `oee` | `oee-v1` | `8h` |

## 2. Configure the template

Select `root.platform.analytics.rollingAvg` — the **Analytics template** inspector opens (not the generic variable grid).

Set:

- **Source path:** `root.platform.devices.demo-sensor-01`
- **Source variable:** `temperature`
- **Window bucket:** `5m`

Save. The **Historian preview** section shows a 24h sparkline when samples exist.

## 3. Apply to a device

In **Apply to device**:

1. Target: `demo-sensor-01` (or a dedicated analytics device).
2. Source variable: `temperature`
3. Click **Apply template**

This applies RELATIVE blueprint `rolling-avg-v1` and creates runtime variable `derivedValue`. The scheduler (`ispf.analytics.derived-tag-tick-ms`) refreshes derived tags; you can also call:

```http
POST /api/v1/platform/analytics/derived-tags/refresh?devicePath=root.platform.devices.demo-sensor-01
```

## 4. Chart widget binding

On a dashboard **chart** widget:

- **Object path:** device with derived tag
- **Variable:** `derivedValue` (or `oeePct` for OEE template)
- **Analytics template:** `rollingAvg` — chart uses historian aggregate bucket from template (`5m`)

Expression shown in UI: `rollingAvg('temperature', '5m')`.

## REST API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/platform/analytics/templates` | List templates |
| `GET` | `/api/v1/platform/analytics/templates/by-path?path=…` | Get one |
| `PUT` | `/api/v1/platform/analytics/templates/by-path?path=…` | Update |
| `POST` | `/api/v1/platform/analytics/templates` | Create custom |
| `DELETE` | `/api/v1/platform/analytics/templates/by-path?path=…` | Delete custom |
| `POST` | `/api/v1/platform/analytics/templates/apply` | Apply blueprint to device |
| `POST` | `/api/v1/platform/analytics/derived-tags/refresh?devicePath=…` | On-demand refresh |

See [reference-asset-analytics.md](../../docs/en/reference-asset-analytics.md) for full reference.
