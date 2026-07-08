# Licensed driver pack pilot (FW-50 staging)

Staging walkthrough for a **signed commercial driver pack** on ISPF ≥ 0.7.6.

Automated acceptance: `com.ispf.server.driver.pack.LicensedDriverPackPilotTest` (signed JAR + `enforce=true`).

## 1. Build pilot JAR

The pilot driver class lives in server tests (`com.ispf.driver.pilot.PilotLicensedDeviceDriver`). For staging, ship your commercial JAR built from a separate module (same `DeviceDriver` SPI).

## 2. Layout on VPS

```text
/opt/ispf/data/drivers/pilot-licensed-pack/
  driver-pack.json
  pilot-licensed-pack.jar
```

Set env:

```bash
export ISPF_DRIVER_PACKS_DIR=/opt/ispf/data/drivers
export ISPF_LICENSE_ENFORCE=true
export ISPF_LICENSE_PUBLIC_KEY_PEM="$(cat /opt/ispf/license/public.pem)"
```

## 3. Sign the pack

1. `GET /api/v1/platform/installation-id` on the target host.
2. `sha256sum pilot-licensed-pack.jar` → `jarSha256`.
3. Sign canonical JSON (`packId`, `minPlatformVersion`, `installationId`, `jarSha256`, `expiresAt`) with vendor private key — see [LICENSED_DRIVER_PACKS.md](../../docs/en/licensed-driver-packs.md) and `tools/license-builder/`.

## 4. Manifest

Copy [driver-pack.json](driver-pack.json) and replace `installationId`, `jarSha256`, `signature`.

## 5. Verify

```bash
systemctl restart ispf-server
curl -s http://localhost:8080/api/v1/drivers | jq '.[] | select(.id=="pilot-licensed")'
```

Expect `pilot-licensed` in the catalog with maturity from the licensed registry.

## Related

- [docs/en/licensed-driver-packs.md](../../docs/en/licensed-driver-packs.md)
- [docs/en/commercial-licensing.md](../../docs/en/commercial-licensing.md)
