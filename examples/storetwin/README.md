# storetwin — Цифровой двойник розничной точки

Self-contained ISPF application for a retail store digital twin. **Not** seeded on an empty platform.

| | |
|---|---|
| **appId** | `storetwin` |
| **schema** | `app_storetwin` |
| **tablePrefix** | `st_` |
| **version** | `1.1.6` |
| **listing** | `listing.manifest.json` · catalog `../marketplace-catalog/storetwin/` |
| **Web UI pack** | `../storetwin-ui/` (`artifactKind: ui-pack`, slug `storetwin-ui`) |

## Install

```bash
# after login — deploy application bundle
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @bundle.json \
  "$ISPF/api/v1/applications/storetwin/deploy"
```

Or Marketplace free install of listing `storetwin` (with `uiPackSlug: storetwin-ui` when ui-pack runtime is available).

Production stands with `require-signed-bundles=true` need a signed `license` block (or piecemeal MCP deploy: migrate → objects → functions → dashboards → reports → operatorUi).

### UI after install

- Hosted SPA: `https://<ispf-host>/apps/storetwin/` (from ui-pack)
- Bridge (demo): `operatorUi.externalSpaUrl` → https://ispf.ai/apps/storetwin/
- Operator: `/?mode=operator&app=storetwin`

See [storetwin-ui/README.md](../storetwin-ui/README.md) for pack build.

## Domain

Equipment / floor plan · planograms (shelves, SKU, issues) · shelf space · incidents & tasks (lifecycle) · KPI · integrations.

## Migrations

- `1.0.0` `st_v1_schema_seed` — embedded in `bundle.json`
- `1.1.0` `st_v2_ui_parity` — also in `sql/st_v2_ui_parity.sql`

## BFF (15 hub functions)

`st_getStore`, `st_listZones`, `st_listEquipment`, `st_listPlanograms`, `st_listPlanogramShelves`, `st_listPlanogramSkus`, `st_listPlanogramIssues`, `st_listShelfSpace`, `st_listIncidents`, `st_listTasks`, `st_listLifecycle`, `st_listKpis`, `st_listIntegrations`, `st_pingIntegration`, `st_listUsers`

Tree script SQL must use `dataSourcePath=root.platform.data-sources.storetwin` (or `deploy_app_function`).

Full map: `bundle.json` → `operatorUi.spaNav`.
