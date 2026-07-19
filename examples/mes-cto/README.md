# MES CTO configurator bundle (BL-223)

Configure-to-order pattern for ISPF manufacturing solutions without custom Java in `ispf-server`.

| Artifact | Purpose |
|----------|---------|
| `bundle.json` | Deploy via `POST /api/v1/applications/mes-cto/deploy` |

## CTO model

The bundle creates an app schema `app_mes_cto` with `mes_cto_` tables for option families, options, compatibility rules, and generated configurations.

Seed data:

| Family | Options |
|--------|---------|
| `finish` | `FINISH-MATTE`, `FINISH-GLOSS` |
| `sensor` | `SENSOR-BASIC`, `SENSOR-VISION` |

Validation rule: `FINISH-MATTE` is incompatible with `SENSOR-VISION`.

## Dependency

`mes-cto` declares a hard bundle dependency on `mes-platform >= 1.4.0`. Deploy `mes-platform` first so the CTO pack sits beside the MES product catalog and uses the same marketplace dependency checks as production bundles.

## Quick start

```bash
curl -X POST http://localhost:8080/api/v1/applications/mes-platform/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform/bundle.json
```

```bash
curl -X POST http://localhost:8080/api/v1/applications/mes-cto/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-cto/bundle.json
```

Open Operator UI: `?mode=operator&app=mes-cto`

## BFF functions

| Function | Purpose |
|----------|---------|
| `mes_cto_listOptions` | Lists seed option families and options |
| `mes_cto_validateConfig` | Returns `OK` or `VALIDATION_FAILED` with messages |
| `mes_cto_generateBuild` | Creates a configuration row and returns `MAT-WIDGET-A01`, `WO-DRAFT-CTO-A01`, and a BoM hint |

Invoke validation with discrete fields:

```bash
curl -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.mes-cto-hub","functionName":"mes_cto_validateConfig","input":{"schema":{"name":"in","fields":[{"name":"finishCode","type":"STRING"},{"name":"sensorCode","type":"STRING"}]},"rows":[{"finishCode":"FINISH-MATTE","sensorCode":"SENSOR-VISION"}]}}'
```

The validation and generate functions also accept `optionsJson` with `finishCode` and `sensorCode`.

## CI

`MesCtoBundleSmokeTest` uses `packages/ispf-server/src/test/resources/mes-cto-bundle.json` and deploys `mes-platform` before `mes-cto` to satisfy `requires[]`.
