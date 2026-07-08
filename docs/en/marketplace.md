> **Language:** Canonical English. Russian edition: [ru/marketplace.md](../ru/marketplace.md).

# Marketplace integration

ISPF platform can browse **remote marketplace servers**, install free bundles, and activate paid listings with an entitlement key.

Default marketplace: [ispf-marketplace](https://github.com/Michaael/ispf-marketplace) (configurable). Vendors may host their own compatible server.

## Configuration

```yaml
ispf:
  marketplace:
    enabled: ${ISPF_MARKETPLACE_ENABLED:true}
    default-id: ${ISPF_MARKETPLACE_DEFAULT_ID:iot-solutions}
    endpoints:
      - id: iot-solutions
        name: IoT Solutions Marketplace
        base-url: ${ISPF_MARKETPLACE_DEFAULT_URL:https://ispf-marketplace.iot-solutions.ru}
        contact-url: ${ISPF_MARKETPLACE_CONTACT_URL:https://iot-solutions.ru}
        default-endpoint: true
      - id: acme
        name: Acme Solutions Store
        base-url: https://marketplace.acme.example
        contact-url: mailto:sales@acme.example
```

| Variable | Default | Description |
|----------|---------|-------------|
| `ISPF_MARKETPLACE_ENABLED` | `true` | Enable remote catalog in System → Solutions |
| `ISPF_MARKETPLACE_DEFAULT_URL` | `https://ispf-marketplace.iot-solutions.ru` | Primary catalog base URL |
| `ISPF_MARKETPLACE_CONTACT_URL` | vendor site | Fallback "contact vendor" link |

## Web console

**System → Solutions → Marketplace**

- Select marketplace endpoint
- Search and filter (free / paid)
- **Free** — one-click install (platform proxies download + deploy)
- **Paid** — enter activation code → signed bundle deploy; without key — link to vendor

## Platform API

| Method | Path |
|--------|------|
| GET | `/api/v1/solutions/marketplaces` |
| GET | `/api/v1/solutions/marketplaces/{id}/catalog?q=&pricing=` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/install` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/activate` |

Paid activate body: `{ "activationCode": "..." }` — `installationId` is added server-side.

## Marketplace server contract

Compatible with [ispf-marketplace](https://github.com/Michaael/ispf-marketplace) API:

- `GET /api/v1/catalog` → `{ listings: [...] }`
- `GET /api/v1/catalog/{slug}/download` (free)
- `POST /api/v1/entitlements/activate` (paid)

Listing fields used by UI: `slug`, `title`, `description`, `pricing`, `appId`, `vendorName`, `vendorLegalName`, `vendorInn`, `vendorSellerKind` (`company` | `individual`), `vendorContactPerson`, `vendorContactEmail`, `vendorContactPhone`, `priceCents`, `latestVersion`, `minIspfVersion`.

## Marketplace GA checklist (BL-183)

Foundation for Phase 32 marketplace GA. Track in release planning; not all items required for dev/lab browse.

| # | Item | Status |
|---|------|--------|
| 1 | Remote catalog browse (System → Solutions) | Shipped |
| 2 | Free one-click install (platform proxies download + deploy) | Shipped |
| 3 | Paid activate with entitlement key | Shipped |
| 4 | Bundle signature verification on install | Planned |
| 5 | Version pinning + upgrade path (`latestVersion`, semver) | Partial |
| 6 | `minIspfVersion` enforcement before install | Partial |
| 7 | Vendor legal fields in listing manifest | Foundation — see demo |
| 8 | Offline/air-gapped bundle import (same manifest) | Shipped via deploy API |
| 9 | Marketplace server artifact reseed runbook | Shipped — see Troubleshooting |
| 10 | 10+ signed production bundles | Planned |
| 11 | 3 external partner catalogs | Planned (BL-184) |
| 12 | CI: bundle validate on publish | Use `tools/bundle-validate-cli/validate.mjs` |

### Demo listing manifest

Reference listing + bundle for marketplace server seeding and integrator tests:

| File | Purpose |
|------|---------|
| [examples/marketplace-demo/listing.manifest.json](../examples/marketplace-demo/listing.manifest.json) | Catalog entry fields (slug, vendor, pricing) |
| [examples/marketplace-demo/bundle.json](../examples/marketplace-demo/bundle.json) | Installable application bundle (`appId=marketplace-demo`) |

Publish flow (marketplace server):

1. Copy `bundle.json` to marketplace artifacts store as `marketplace-demo__1.0.0.json`
2. Register `listing.manifest.json` in catalog index
3. Verify `GET /api/v1/catalog/marketplace-demo/download` and ISPF **Install**

## Troubleshooting

### `ENOENT ... warehouse-reference__1.0.0.json` on download / ISPF install 502

Catalog (`GET /api/v1/catalog`) works, but **Download bundle** or ISPF **Install** fails — bundle JSON is missing on the marketplace server (host path vs Docker volume mismatch).

On the **marketplace VPS** (`ispf-marketplace.iot-solutions.ru`):

```bash
cd /opt/ispf-marketplace
git pull origin main
bash deploy/vps-reseed-artifacts.sh
```

ISPF only proxies the download; fix is always on the marketplace host. See [ispf-marketplace/deploy/README.md](https://github.com/Michaael/ispf-marketplace/blob/main/deploy/README.md).

## Related

- [competitive-scorecard.md](competitive-scorecard.md) — dimension 12 (ecosystem / marketplace)
- [commercial-licensing.md](commercial-licensing.md)
- [plugins.md](plugins.md)
- [applications.md](applications.md)
