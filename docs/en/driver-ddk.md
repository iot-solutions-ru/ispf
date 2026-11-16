> **Language:** Canonical English. Russian edition: [ru/driver-ddk.md](../ru/driver-ddk.md).

# Driver Development Kit (DDK)

> **Status:** Stable — Custom driver SDK. Hub: [doc-status.md](doc-status.md).

**BL-144** — scaffold for custom driver packs outside the core monorepo or as a new `ispf-driver-*` module.

## When to use

- Partner protocol not included in the Apache/GPL bundle.
- Customer-internal driver with a separate release cycle.
- Prototype before promotion in `DriverProductionMatrix`.

## Artifacts

| Path | Description |
| ---- | -------- |
| [`packages/ispf-driver-ddk`](../../packages/ispf-driver-ddk/) | DDK Gradle module (template sources + smoke test) |
| [`packages/ispf-driver-ddk/template/`](../../packages/ispf-driver-ddk/template/) | Copyable stub: driver, test, `driver-pack.json`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` |
| [licensed-driver-packs](licensed-driver-packs.md) | Runtime layout and licensing |
| [driver-promotion](driver-promotion.md) | PRODUCTION checklist |
| [driver-interop-lab](driver-interop-lab.md) | CI interop after promotion |

## Workflow

### 1. Build outside this repository

`packages/ispf-driver-ddk/template/` is its own Gradle build. `com.ispf:ispf-core` and `com.ispf:ispf-driver-api` are published by this repository into the local Maven repository.

From an ISPF checkout:

```bash
./gradlew publishDdkApi
```

That writes both modules to `~/.m2` at the `version` in `gradle.properties`. Copy `template/` out. Set `ispfVersion` in the copy's `gradle.properties` to that same version. JDK 25, Gradle 9.9:

```bash
gradle test
gradle assembleDriverPack
```

The pack is `build/driver-pack/ispf-driver-acme-widget/` (`driver-pack.json` and `ispf-driver-acme-widget.jar`). `assembleDriverPack` builds a fat jar: third-party `implementation` libraries are embedded; `com.ispf:*` stays `compileOnly` because the server already loads the SPI. When you rename the driver, change `packId` in `build.gradle.kts`, `rootProject.name` in `settings.gradle.kts`, and `packId`, `drivers[].driverId`, and `drivers[].driverClass` in `driver-pack.json`. `assembleDriverPack` names the jar `$packId.jar`. Set `jarFile` to that name. `driverClass` is the class the server loads.

A module that lives in this repository is a new `packages/ispf-driver-<id>` project, a `project(":packages:ispf-driver-api")` dependency, and a row in `gradle/driver-packs.json`. The root build applies `ispf-driver-pack` only to those modules. Do not register `template/` in the root `settings.gradle.kts`.

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

Outside this repository, `gradle assembleDriverPack` writes `build/driver-pack/ispf-driver-acme-widget/`.

Inside this repository, after the module is in `gradle/driver-packs.json`:

```bash
./gradlew :packages:ispf-driver-acme-widget:assembleDriverPack
```

The pack directory is `packages/ispf-driver-acme-widget/build/driver-pack/ispf-driver-acme-widget/`. `syncAllDriverPacks` copies every pack listed in `gradle/driver-packs.json` to the root `build/driver-packs/`. `syncDevDriverPacks` copies only the modules named in `devDriverPackProjectNames` in the root `build.gradle.kts`.

### 5. Deploy pack

Copy directory to `${ISPF_DRIVER_PACKS_DIR}/` on server. Restart ISPF → `LicensedDriverPackLoader` registers the driver.

### 6. Promotion

1. Entry in `DriverProductionMatrix` with `loopbackTestSourcePath` and `interopGradleModule`.
2. Add module to [`.github/workflows/driver-interop.yml`](../../.github/workflows/driver-interop.yml).
3. Update [drivers](drivers.md).

## Point mapping convention (template)

Format: `channel:address` (example `ai:room-1`). Replace with your DSL (Modbus-style, OPC NodeId, etc.).

## Reference drivers (roadmap.md)

BL-144 acceptance: three reference custom drivers — `template/` (acme-widget), `examples/simple-counter/`, `examples/json-poller/` (real HTTP JSON poll), `examples/modbus-simulator/`. All compile and pack via:

```bash
./gradlew :packages:ispf-driver-ddk:check
# → build/ddk-example-packs/ispf-driver-{simple-counter,json-poller,modbus-simulator}/
```

CI: `.github/workflows/driver-interop.yml` matrix includes `ispf-driver-ddk`.

## Related ADRs

- [0022-driver-production-matrix](decisions/0022-driver-production-matrix.md)
- [0002-dogfooding-gate](decisions/0002-dogfooding-gate.md)
