> **Language:** Canonical English. Russian edition: [ru/virt-cluster.md](../ru/virt-cluster.md).

# Virtual cluster reference

> **Status:** Stable — one install call for the built-in virtual cluster. Status tags: [doc-status.md](doc-status.md).

`POST /api/v1/platform/presets/virt-cluster` installs one reference project: three virtual devices, a hub that copies their sine values, an error flag, two dashboards, an alert, and an operator app. This is the only path under `/api/v1/platform/presets/`. Other solutions are cards on **System**, tab **Solutions**. A project with different names is a `bundle.json` deploy: [Solution developer guide](solution-developer-guide.md#step-4-deploy-as-a-single-bundlejson-recommended).

`admin` or `developer` may call it. `operator` receives 403. An empty body uses the defaults below. A second call updates the same paths.

```bash
curl -sS -X POST http://localhost:8080/api/v1/platform/presets/virt-cluster \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"wireOperatorApp\":true,\"operatorAppId\":\"platform\"}"
```

| Field | Default | Meaning |
|-------|---------|---------|
| `wireOperatorApp` | `true` | Create or update the operator app |
| `operatorAppId` | `platform` | App id in `?mode=operator&app=` |

The response includes `status` `OK`, the paths below, and `operatorUrlHint` when the operator app is wired.

## What it creates

| Path | What it is |
|------|------------|
| `root.platform.devices.virt-cluster` | Folder, type `CUSTOM` |
| `.dev-01`, `.dev-02`, `.dev-03` under that folder | Devices, type `DEVICE`, model `virtual-lab-v1`, driver `virtual`, poll 2000 ms, auto-start on |
| `.hub` under that folder | Hub, type `CUSTOM`. Variables `member1Sine`, `member2Sine`, `member3Sine`, `clusterError` |
| `root.platform.dashboards.virt-cluster-overview` | Indicator **Cluster ERROR** and a device table |
| `root.platform.dashboards.virt-cluster-detail` | Three values and three charts for the device chosen in the table |
| `root.platform.alert-rules.virt-cluster-error` | Alert on the hub |
| operator app `platform` | Title "Platform HMI", default dashboard the overview, alarm bar on |

The install starts each device driver and polls once.

## Operator screen

From a source checkout the console is `http://localhost:5173/?mode=operator&app=platform`. The portable zip and the container use port 8080 with the same query. Another `operatorAppId` changes that `app` value.

The overview table uses `selectionKey` `device`. A row opens the detail dashboard (`rowTargetDashboard`, `rowOpenMode` `navigate`). Detail widgets read the selected device. Widget fields: [Widgets](widgets.md) and [Dashboards](dashboards.md).

The alarm bar is on that operator app: top, minimum level `WARNING`, event name `virtClusterError`. It is not a widget on the admin dashboard. An `operator` account opens this app right after sign-in when `autoStartEnabled` is true and `autoStartApp` is `platform`. `admin` and `developer` still open the admin console. They use the operator URL above. [Security](security.md).

## Waves, copy, and the alert

Each poll of driver `virtual` writes `sineWave`, `sawtoothWave`, and `triangleWave` together. There is no switch that picks one wave. The preset sets amplitudes 10, 5, and 5, and `periodSec` 30. Driver page: [virtual](drivers.md#virtual-ispf-driver-virtual).

The preset turns `historyEnabled` on for those three variables. A chart of a variable stays empty until that flag is on. On the `local` profile the samples are in the H2 file `data/ispf-local`, not in ClickHouse. [Variable history](variable-history.md).

The hub copies each device with a binding. `read("root.platform.devices.virt-cluster.dev-01/sineWave")` writes `member1Sine`. The same form covers `dev-02` → `member2Sine` and `dev-03` → `member3Sine`. The error flag is:

```text
self.member1Sine["value"] > 0 && self.member2Sine["value"] > 0 && self.member3Sine["value"] > 0
```

That result is written to `clusterError`. [Bindings](bindings.md).

The alert watches the hub variable `clusterError`. The condition is `self.clusterError["value"] == true`, `edgeTrigger` is true, priority is `HIGH`, and the event name is `virtClusterError`.
