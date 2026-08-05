# oil-control — Ойл Контроль (АЗС)

Self-contained ISPF application for fuel quantity & quality control on a petrol-station network. **Not** seeded on an empty platform.

| | |
|---|---|
| **appId** | `oil-control` |
| **schema** | `app_oil_control` |
| **tablePrefix** | `oc_` |
| **version** | `0.5.0` |
| **listing** | `listing.manifest.json` · catalog `../marketplace-catalog/oil-control/` |
| **Web UI pack** | `../oil-control-ui/` (`artifactKind: ui-pack`, slug `oil-control-ui`) |
| **BFF contract** | `api/openapi.yaml` |

## Install

```bash
# after login — deploy application bundle
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @bundle.json \
  "$ISPF/api/v1/applications/oil-control/deploy"
```

Or Marketplace free install of listing `oil-control` (with `uiPackSlug: oil-control-ui` when ui-pack runtime is available).

Production stands with `require-signed-bundles=true` need a signed `license` block.

### UI after install

- Hosted SPA: `https://<ispf-host>/apps/oil-control/` (from ui-pack)
- Bridge (demo): `operatorUi.externalSpaUrl` → http://82.146.32.188/

See [oil-control-ui/README.md](../oil-control-ui/README.md) for pack build (`npm run pack:ui` in SPA repo).

## Modules

Balance · monitoring map · tank stocks · imbalances · RGS–TRK · calibration · manual measurement · product receipt · logistics · quality · assets · incidents · KPI.

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
