# farmtwin — Цифровой двойник фермы

Self-contained ISPF application for a swine-complex digital twin. **Not** seeded on an empty platform.

| | |
|---|---|
| **appId** | `farmtwin` |
| **schema** | `app_farmtwin` |
| **tablePrefix** | `ft_` |
| **version** | `1.1.0` |
| **listing** | `listing.manifest.json` · catalog `../marketplace-catalog/farmtwin/` |
| **Web UI pack** | `../farmtwin-ui/` (`artifactKind: ui-pack`, slug `farmtwin-ui`) |

## Install

```bash
# Marketplace free install (pulls companion ui-pack via uiPackSlug)
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$ISPF/api/v1/solutions/marketplaces/default-publisher/listings/farmtwin/install"
```

Or deploy application bundle + install ui-pack separately:

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @bundle.json \
  "$ISPF/api/v1/applications/farmtwin/deploy"

# ui-pack drop-in → ISPF_UI_PACKS_DIR/farmtwin/
```

Production stands with `require-signed-bundles=true` need a signed `license` block (or piecemeal MCP deploy: migrate → objects → functions → dashboards → reports → operatorUi).

### UI after install

- Hosted SPA: `https://<ispf-host>/apps/farmtwin/` (from ui-pack)
- Operator: `/?mode=operator&app=farmtwin` → **Open app UI**
- Bridge (optional): `operatorUi.externalSpaUrl` → https://ispf.ai/apps/farmtwin/

See [farmtwin-ui/README.md](../farmtwin-ui/README.md) and [MARKETPLACE.md](./MARKETPLACE.md).

## Domain

Sites / sections · alarms · trends · climate · feeding · livestock health · video analytics · robots · RFID traceability · tasks · SPPR · integrations · KPI.
