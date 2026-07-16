> **Language:** Canonical English. Russian edition: [ru/driver-ddk.md](../ru/driver-ddk.md).

# Driver Development Kit (DDK)

**BL-144** — scaffold for custom driver packs outside the core monorepo or as a new `ispf-driver-*` module.

## When to use

- Partner protocol not included in the Apache/GPL bundle.
- Customer-internal driver with a separate release cycle.
- Prototype before promotion in `DriverProductionMatrix`.

## Artifacts

| Path | Description |
| ---- | -------- |
| [`packages/ispf-driver-ddk`](../../packages/ispf-driver-ddk/) | DDK Gradle module (template sources + smoke test) |
| [`packages/ispf-driver-ddk/template/`](../../packages/ispf-driver-ddk/template/) | Copyable stub: driver, test, `driver-pack.json`, `build.gradle.kts` |
| [licensed-driver-packs](licensed-driver-packs.md) | Runtime layout and licensing |
| [driver-promotion](driver-promotion.md) | PRODUCTION checklist |
| [driver-interop-lab](driver-interop-lab.md) | CI interop after promotion |

## Workflow

### 1. Create module

```bash
cp -r packages/ispf-driver-ddk/template packages/ispf-driver-acme-widget
```

Edit `driverId`, package, `DriverMetadata`, point mapping parser.

Add to `settings.gradle.kts`:

```kotlin
"packages:ispf-driver-acme-widget",
```

Module automatically receives plugin `ispf-driver-pack` (see root `build.gradle.kts`).

### 2. Implement SPI

`DeviceDriver` ([`ispf-driver-api`](../../packages/ispf-driver-api/src/main/java/com/ispf/driver/DeviceDriver.java)):

- `metadata()` — `driverId`, config schema, maturity
- `initialize` / `connect` / `disconnect`
- `readPoints(Map<variableName, pointMapping>)`
- `writePoint` — when capability `write` is declared

**Ingress rule:** hot path does not write to DB; only `updateVariable` ([ADR ingress contract](../../packages/ispf-driver-api/src/main/java/com/ispf/driver/DeviceDriver.java)).

### 3. Loopback test

At least one JUnit test with stub server on `127.0.0.1` (see `TemplateDeviceDriverTest`). No hardware in CI.

### 4. Build pack

```bash
./gradlew :packages:ispf-driver-acme-widget:assembleDriverPack
ls build/driver-packs/ispf-driver-acme-widget/
```

### 5. Deploy pack

Copy directory to `${ISPF_DRIVER_PACKS_DIR}/` on server. Restart ISPF → `LicensedDriverPackLoader` registers the driver.

### 6. Promotion

1. Entry in `DriverProductionMatrix` with `loopbackTestSourcePath` and `interopGradleModule`.
2. Add module to [`.github/workflows/driver-interop.yml`](../../.github/workflows/driver-interop.yml).
3. Update [drivers](drivers.md).

## Point mapping convention (template)

Format: `channel:address` (example `ai:room-1`). Replace with your DSL (Modbus-style, OPC NodeId, etc.).

## Reference drivers (roadmap.md)

BL-144 acceptance: three reference custom drivers — `template/` (acme-widget), `examples/simple-counter/`, `examples/json-poller/`, `examples/modbus-simulator/`. All four compile in `:packages:ispf-driver-ddk:test`.

## Related ADRs

- [0022-driver-production-matrix](decisions/0022-driver-production-matrix.md)
- [0002-dogfooding-gate](decisions/0002-dogfooding-gate.md)
