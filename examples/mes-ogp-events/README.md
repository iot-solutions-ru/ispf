# MES OGP Event Registration (UC-25)

Reference application for **production event registration** on ISPF: unprocessed machine signals, operator registration via **function-form**, process journal via **report**, roll traceability (white/grey map), roll labels, shift supervisor view, 1C outbox. UI uses only standard dashboard widgets (`value`, `report`, `function-form`, `function`, `event-feed`).

`appId`: `mes-ogp-events`

## Quick start

```powershell
# Generate bundle (after editing generate_bundle.mjs)
node examples/mes-ogp-events/generate_bundle.mjs

# Deploy (server running, admin role)
.\examples\mes-ogp-events\deploy.ps1
```

Operator UI: `?mode=operator&app=mes-ogp-events`

| Dashboard | Path |
|-----------|------|
| Event registration | `root.platform.dashboards.ogp-operator-hmi` |
| Simulator | `root.platform.dashboards.ogp-simulator` |
| Shift supervisor | `root.platform.dashboards.ogp-shift-supervisor` |
| Admin codes | `root.platform.dashboards.ogp-admin-codes` |
| Roll release | `root.platform.dashboards.ogp-roll-release` |

## Hub object

BFF functions: `root.platform.devices.ogp-mes-hub`

Simulated line: `root.platform.devices.ogp-line-01` (SQL `machine_state` + schedule `ogp-meter-tick` / SQL bindings — no virtual driver profiles)

## CI

Copy bundle to test resources and run smoke test:

```powershell
Copy-Item examples/mes-ogp-events/bundle.json packages/ispf-server/src/test/resources/mes-ogp-events-bundle.json
.\gradlew :packages:ispf-server:test --tests com.ispf.server.application.MesOgpEventsBundleSmokeTest
```

## Related docs

- [REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md](../../docs/en/reference-mes-ogp-events-walkthrough.md)
