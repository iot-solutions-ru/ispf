# oil-control — Ойл Контроль (АЗС)

Self-contained ISPF application for fuel quantity & quality control on a petrol-station network. **Not** seeded on an empty platform.

| | |
|---|---|
| **appId** | `oil-control` |
| **schema** | `app_oil_control` |
| **tablePrefix** | `oc_` |
| **version** | `0.5.0` |
| **listing** | `listing.manifest.json` · catalog `../marketplace-catalog/oil-control/` |
| **BFF contract** | `api/openapi.yaml` |
| **React SPA** | separate package `oil-control-azs-web` (not in marketplace JAR) |

## Install

```bash
# after login — deploy bundle (unsigned stepwise / signed import_package on prod)
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @bundle.json \
  "$ISPF/api/v1/applications/oil-control/deploy"
```

Or agent: `import_package` / `validate_bundle` with this manifest.  
Production stands with `require-signed-bundles=true` need a signed `license` block.

## Modules

Balance · monitoring map · tank stocks · imbalances · RGS–TRK (dispense-system) · calibration / tank assessment · manual measurement · product receipt · supply / tanker logistics · quality & sampling acts · assets & work orders · incidents · KPI.

## Migrations

Source SQL in `sql/` (`V1`…`V8`); same scripts are embedded in `bundle.json` → `migrations`.

## SPA ↔ BFF (46 hub functions)

| SPA route | BFF (examples) |
|---|---|
| `/balance` | `oc_listImbalances`, `oc_calcBalance`, `oc_listBalanceTrend` |
| `/map` | `oc_listStations`, `oc_listMonitorEvents` |
| `/rgs-trk` | `oc_listRgsTrk`, `oc_listRgsTrkSeries` |
| `/calibration` | `oc_listCalibrations`, `oc_getCalibrationProfile` |
| `/quality` | `oc_listBatches`, `oc_createLabSample` |
| `/mgmt` | `oc_listKpiCards`, `oc_listKpiReports` |

Full map: `bundle.json` → `operatorUi.spaNav`.
