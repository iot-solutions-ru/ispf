# Analytics KPI demo pack (Tier C / BL-216)

Free marketplace listing for historian helper **`percentChange`**.

## Build artifact

From repo root:

```powershell
.\gradlew :packages:ispf-analytics-marketplace-demo:assembleAnalyticsMarketplaceZip
```

Produces:

- `analytics-pack-demo-1.0.0.zip` — installable archive
- `listing.manifest.json` — marketplace catalog entry

## Local install (dev/lab)

With ISPF server running from repo:

```http
GET  /api/v1/marketplace/analytics-packs
POST /api/v1/marketplace/analytics-packs/ispf-analytics-kpi-demo/install
```

Verify:

```http
GET /api/v1/platform/analytics/catalog/percentChange
```

## Example historian rule

```text
percentChange(root.platform.devices.tank-01.level, 1h)
```

Output: percent change between first and last bucket average in the window (e.g. `25.0` = +25%).

## Publish to remote marketplace

On [ispf-marketplace](https://github.com/Michaael/ispf-marketplace) host:

1. Copy `analytics-pack-demo-1.0.0.zip` to artifacts store as `ispf-analytics-kpi-demo__1.0.0.zip`
2. Register `listing.manifest.json` in catalog index
3. `bash deploy/vps-reseed-artifacts.sh`
4. Install from **System → Solutions → Marketplace** or `POST /api/v1/solutions/marketplaces/{id}/listings/ispf-analytics-kpi-demo/install`
