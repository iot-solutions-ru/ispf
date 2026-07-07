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
| `ISPF_MARKETPLACE_CONTACT_URL` | vendor site | Fallback “contact vendor” link |

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

Listing fields used by UI: `slug`, `title`, `description`, `pricing`, `appId`, `vendorName`, `vendorContactEmail`, `vendorContactUrl`, `priceCents`, `latestVersion`, `minIspfVersion`.

## Related

- [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md)
- [PLUGINS.md](PLUGINS.md)
- [APPLICATIONS.md](APPLICATIONS.md)
