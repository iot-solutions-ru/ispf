> **Language:** Canonical English. Russian edition: [ru/reference-mes-ogp-events-walkthrough.md](../ru/reference-mes-ogp-events-walkthrough.md).

# MES OGP Events — walkthrough (UC-25)

Reference application `mes-ogp-events` demonstrates production event registration on ISPF without custom Java in `ispf-server`. Business logic lives in the application bundle under `examples/mes-ogp-events/`.

## Deploy

```powershell
node examples/mes-ogp-events/generate_bundle.mjs
.\examples\mes-ogp-events\deploy.ps1
```

Operator UI: `?mode=operator&app=mes-ogp-events`

Hub object (BFF): `root.platform.devices.ogp-mes-hub`

## Scenario 1 — Setup delete-now (code 120)

1. Open **OGP Simulator** dashboard.
2. Run `ogp_seedDemoStage` if the stage is empty.
3. Click **stop** signal (`ogp_simulateSignal`) — creates an unprocessed downtime row.
4. Open **Event registration** dashboard (`ogp-operator-hmi`).
5. In **Unprocessed events** report copy the row ID if needed.
6. Fill **Event registration** function-form: code **120**, delete-now, roll **0**, meters, times, comment → **Register**.
7. **Process journal** report shows the new row with status **Deleted**.
8. Roll map gets a **grey** interval on roll `0` (deleted event footprint).

## Scenario 2 — Paint splashes register-for-next (code 141)

1. Simulate **defect button** or **knife** signal.
2. In wizard step 1 choose **141 Paint splashes**, uncheck **Delete now** in the registration form.
3. Select production roll **P1**, side PM/Passer, affected rows.
4. Register — journal status **Registered**, **white** interval on roll map.

## Scenario 3 — Unprocessed table and grouping (US-139)

1. Fire two **stop** signals within `downtime_group_timeout_sec` (default 300 s).
2. Second signal merges into one unprocessed row (`member_count` > 1).
3. Delete selected rows using **Delete unprocessed** function-form (IDs from report + comment).

## Scenario 4 — Shift supervisor (US-77)

1. Open **Shift supervisor** dashboard.
2. Downtime summary report groups delete-now events by order/stage.
3. **Record correction** function-form updates journal rows (`ogp_updateProcessEvent`).

## Scenario 5 — Roll release label (US-08)

1. Open **Roll release** dashboard.
2. View roll map intervals and pending white-map defects.
3. Run `ogp_buildRollLabel` for roll `P1` — snapshot stored in `roll_label_data`.

## Scenario 6 — 1C export (US-17)

1. Each registered event (except codes with `exclude_from_1c`) enqueues JSON in `integration_outbox`.
2. Schedule `ogp-1c-export` or workflow `ogp-export-1c` runs `ogp_export1cBatch`.
3. Dev: rows marked `sent` without external HTTP (mock URL in `app_setting`).

## Operator dashboards (standard widgets)

| Dashboard | Widgets |
|-----------|---------|
| `ogp-operator-hmi` | `value` strip + `tab-panel`: **Registration** (unprocessed report, wizard `function-form` 3 steps, delete form), **Journal** |
| `ogp-shift-supervisor` | `tab-panel`: **Downtime**, **Journal** (report + edit form), **1C** (integration outbox) |
| `ogp-simulator` | `function-form`, `function`, `event-feed` |
| `ogp-admin-codes` | `report`, `function-form` |
| `ogp-roll-release` | `report`, `function-form` |

## Machine signals (simulator)

Virtual driver profile `ogp-print-line` on `root.platform.devices.ogp-line-01` updates telemetry (`windingRoll`, `speedMpm`, `meterM`). Use simulator dashboard function-form or `ogp_simulateSignal` for stop / knife / defect / resume.

## CI smoke test

`MesOgpEventsBundleSmokeTest` deploys the bundle, simulates a stop, registers event 120 delete-now, verifies journal and 1C batch export.

## Regenerate bundle after script changes

```powershell
node examples/mes-ogp-events/generate_bundle.mjs
Copy-Item examples/mes-ogp-events/bundle.json packages/ispf-server/src/test/resources/mes-ogp-events-bundle.json
```
