# BL-144 Driver DDK — lab GA gate (2026-10-15)

**Status:** Lab Done (CI).

## Acceptance mapping

| Requirement | Evidence |
|-------------|----------|
| `packages/ispf-driver-ddk` + template | Module + `template/` |
| 3 reference custom drivers | `simple-counter`, `json-poller` (HTTP JSON), `modbus-simulator` |
| Pack assemble | `./gradlew :packages:ispf-driver-ddk:assembleDdkExamplePacks` → `build/ddk-example-packs/` |
| Promotion docs | [driver-ddk](../../en/driver-ddk.md), [driver-promotion](../../en/driver-promotion.md) § DDK |
| CI | `driver-interop.yml` matrix `ispf-driver-ddk` |

## Commands

```bash
./gradlew :packages:ispf-driver-ddk:check
```
