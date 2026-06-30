# Licensed driver packs (FW-50)

All ISPF device drivers ship as **driver packs** (1 driver = 1 pack = 1 `licenseType`).  
The platform JAR contains only the pack loader — not protocol implementations.

## Layout

```text
${ISPF_DRIVER_PACKS_DIR}/
  acme-opc-premium/
    driver-pack.json
    acme-opc-premium.jar
```

## Build (monorepo)

```bash
./gradlew syncAllDriverPacks
```

Output: `build/driver-packs/<packId>/` with `driver-pack.json`, `LICENSE`, and JAR.

Catalog source: [gradle/driver-packs.json](../gradle/driver-packs.json) (regenerate via `python tools/generate-driver-packs-json.py`).

## Configure

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
| `driverId` | yes | Runtime driver id (e.g. `modbus-tcp`) |
| `licenseType` | yes | SPDX id: `Apache-2.0`, `GPL-3.0-only`, `LGPL-3.0-or-later`, `MPL-2.0`, `LicenseRef-StepFunc-NonCommercial`, … |
| `externalDependencies` | when third-party JAR is not bundled | e.g. StepFunc DNP3 — see `NOTICE-EXTERNAL-DEPS.txt` |
| `minPlatformVersion` | yes | Semver gate (e.g. `0.9.32`) |
| `jarFile` | yes | JAR filename relative to pack directory |
| `drivers[]` | yes | `driverId` + `driverClass` |
| `license` | when `enforce=true` for commercial packs | RSA-signed claims (see below) |

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

## Deploy profiles

Production VPS deploy uses **`permissive`** profile by default (excludes copyleft and StepFunc-restricted packs). See [LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md).

## Related

- [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) — RSA keys and `tools/license-builder/`
- [DRIVERS.md](DRIVERS.md) — in-tree catalog
- [PLATFORM_DEVELOPER_BACKLOG.md §12.8](PLATFORM_DEVELOPER_BACKLOG.md#128-req-fw-50--licensed-driver-jar-contract)
