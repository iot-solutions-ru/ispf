# Licensed driver packs (FW-50)

Commercial protocol drivers ship **outside** `main` as a signed JAR + `driver-pack.json`. Apache in-tree drivers are unchanged.

## Layout

```text
${ISPF_DRIVER_PACKS_DIR}/
  acme-opc-premium/
    driver-pack.json
    acme-opc-premium.jar
```

Configure:

```yaml
ispf:
  driver:
    packs-dir: ${ISPF_DRIVER_PACKS_DIR:./data/drivers}
  license:
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

## `driver-pack.json`

| Field | Required | Description |
|-------|----------|-------------|
| `packId` | yes | Unique pack identifier |
| `minPlatformVersion` | yes | Semver gate (e.g. `0.7.5`) |
| `jarFile` | yes | JAR filename relative to pack directory |
| `drivers[]` | optional | Explicit `driverId` + `driverClass`; if omitted, `ServiceLoader` on `DeviceDriver` |
| `license` | when `enforce=true` | RSA-signed claims (see below) |

Example:

```json
{
  "packId": "acme-opc-premium",
  "minPlatformVersion": "0.7.5",
  "jarFile": "acme-opc-premium.jar",
  "drivers": [
    {
      "driverId": "acme-opc-premium",
      "driverClass": "com.acme.driver.OpcPremiumDeviceDriver"
    }
  ],
  "license": {
    "packId": "acme-opc-premium",
    "minPlatformVersion": "0.7.5",
    "installationId": "<from GET /api/v1/platform/installation-id>",
    "jarSha256": "<sha256 of JAR bytes>",
    "expiresAt": "2027-12-31T23:59:59Z",
    "signature": "<base64 RSA-SHA256 over canonical license payload>"
  }
}
```

License signing payload fields (sorted JSON): `packId`, `minPlatformVersion`, `installationId`, `jarSha256`, `expiresAt`.

## Runtime behaviour

1. On startup `LicensedDriverPackLoader` scans each subdirectory for `driver-pack.json`.
2. When `license` is present, `DriverPackLicenseVerifier` checks signature, installation, expiry, and JAR hash.
3. Valid packs register in `LicensedDriverRegistry`; `DriverFactory` and `DriverCatalog` merge licensed drivers.
4. Invalid or missing license: pack skipped + **WARN** (`enforce=false`) or skip + **ERROR** log (`enforce=true`).

## SPI contract

- JAR must implement `com.ispf.driver.DeviceDriver`.
- Prefer explicit `drivers[]` with `driverClass` for predictable `driverId`.
- Promotion path for in-tree stubs: [DRIVER_PROMOTION.md](DRIVER_PROMOTION.md).

## Related

- [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) — RSA keys and `tools/license-builder/`
- [DRIVERS.md](DRIVERS.md) — in-tree catalog
- [PLATFORM_DEVELOPER_BACKLOG.md §12.8](PLATFORM_DEVELOPER_BACKLOG.md#128-req-fw-50--licensed-driver-jar-contract)
