> **Language:** Canonical English. Russian edition: [ru/reference-mes-ogp-events-walkthrough.md](../ru/reference-mes-ogp-events-walkthrough.md).

# Reference Walkthrough: Roll-to-Roll Line Downtime and Defect Tracking (`mes-ogp-events`)

> **Status:** Lab — reference application for web/roll production lines: machine stop and defect registration, roll map intervals, shift supervisor adjustments, and 1C outbox export. Status tags: [doc-status.md](doc-status.md).

The **`mes-ogp-events`** bundle ([`examples/mes-ogp-events/`](../../examples/mes-ogp-events/)) demonstrates an operator and shift-supervisor workplace for a printing or converting line built entirely with declarative ISPF widgets, reports, and script functions.

---

## 1. Deploying and Opening the Application

```powershell
node examples/mes-ogp-events/generate_bundle.mjs
.\examples\mes-ogp-events\deploy.ps1
```

- **Operator UI URL:** `?mode=operator&app=mes-ogp-events`
- **Application Logic Hub (`SINGLETON`):** `root.platform.singleton-blueprints.mes-ogp-events-hub-v1`
- **Simulated line telemetry (`DEVICE`):** `root.platform.devices.ogp-line-01` (virtual profile `ogp-print-line`: `windingRoll`, `speedMpm`, `meterM`)

---

## 2. Key Operational Scenarios

### Scenario 1 — Immediate Cutout Setup Event (Code `120`)
1. Open the **OGP Simulator** dashboard (`ogp-simulator`), seed the demo stage (`ogp_seedDemoStage`), and trigger a **stop** signal (`ogp_simulateSignal`).
2. Switch to the **Event Registration** dashboard (`ogp-operator-hmi`).
3. In the registration form, select code **`120` (Setup)** with immediate cutout (`delete-now`), roll `0`, meter range, and comment → click **Register**.
4. The **Process Journal** report shows the new entry with status **Deleted**, and the roll map records a grey cutout interval.

### Scenario 2 — Defect Logged for Downstream Slitting (Code `141`)
1. Trigger a **defect button** or **knife** signal on the simulator.
2. Select code **`141` (Paint splashes)**, uncheck immediate cutout, choose roll **`P1`**, and specify the affected lanes.
3. The defect is recorded with status **Registered** and appears as a white inspection interval on the roll map.

### Scenario 3 — Automatic Grouping of Short Stops
Two **stop** signals arriving within `downtime_group_timeout_sec` (default `300` s) automatically merge into a single unprocessed downtime row (`member_count > 1`).

### Scenario 4 — Shift Supervisor Review, Roll Release Label, and 1C Outbox Export
- On the **Shift Supervisor** dashboard (`ogp-shift-supervisor`), supervisors review grouped downtime reports and adjust journal records via `ogp_updateProcessEvent`.
- On the **Roll Release** dashboard (`ogp-roll-release`), `ogp_buildRollLabel` generates the release passport for roll `P1` in `roll_label_data`.
- Registered events enqueue JSON messages in `integration_outbox`, which are processed in batches by schedule `ogp-1c-export` (`ogp_export1cBatch`).

Run the automated smoke test:

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesOgpEventsBundleSmokeTest"
```
