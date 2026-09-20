# ADR-0059: Toolchain and dependency version policy

## Status

Accepted (2026-09-20)

## Context

The September 2026 code analysis flagged the stack as "bleeding edge": Spring Boot 4.1, Java 25, TypeScript 7.0, Vite 8, React 19, antd 6, Gradle 9.7. Symptoms already visible in the build:

- `build.gradle.kts` force-pins `protobuf-java` 4.36.x (CEL vs OTel gencode), `kafka` 4.3.1 and a `netty-bom` for tests — each pin is a workaround for an upstream mismatch.
- `hadoop-common` 3.5.0 is pulled into `ispf-server` only for Parquet export; it is the single largest transitive dependency tree and CVE surface in the JAR.
- TypeScript 7.0 (Go compiler) ships no programmatic API, so `typescript-eslint` cannot run against it; the console needs the `@typescript/typescript6` side-by-side alias.
- No written rule tells contributors whether to take a major upgrade the week it ships or wait.

ISPF is a self-hosted industrial SCADA platform: operators expect long-lived installs, air-gapped upgrades and predictable JDK requirements.

## Decision

### 1. Two lanes

| Lane | Components | Policy |
|------|------------|--------|
| **Runtime contract** | JDK, Spring Boot major, PostgreSQL/TimescaleDB major, Node LTS for the console build | **N or N-1 LTS/GA**. A new major is adopted only after its first patch release (x.y.1+) *and* after the previous major has been supported in ISPF for at least one minor ISPF release. Documented in [getting-started](../getting-started.md) and the release notes. |
| **Build/dev tooling** | Gradle, Vite, Vitest, Playwright, ESLint, TypeScript | May track latest, provided `npm ci` / `./gradlew` on a clean machine works with the pinned lock files. Breakage is a CI failure, never an operator-visible one. |

### 2. JDK

- Supported runtime JDK: **25 (current)**; the previous LTS (21) is a build target only where explicitly needed (edge agent), otherwise dropped.
- JDK bump requires: JaCoCo, Byte Buddy/Mockito and Spring Boot support confirmed; `Dockerfile`, portable zip and `deploy/` updated in the same PR.

### 3. Pins are debt with an owner

Every `force(...)`, `extra["*.version"]`, `enforcedPlatform` or npm `overrides` entry must carry a comment with (a) the reason and (b) the upstream issue or release that lets us remove it. Nightly runs `./gradlew dependencyUpdates`-style reporting (Dependabot/Renovate already open PRs); the owner of a pin reviews it at each Spring Boot minor.

#### Pin registry

The registry below is the single list of live pins. A PR that adds a pin adds a row; a PR that removes the upstream cause removes the row. Owner = the area team that reviews Dependabot PRs for that package.

| Pin | Where | Why | Remove when | Owner | Re-check |
|-----|-------|-----|-------------|-------|----------|
| `protobuf-java(-util,-javalite)` **4.36.1** (`force`) | root `build.gradle.kts` | CEL 0.14+ gencode needs runtime ≥ gencode; Micrometer/OTel transitively bring 4.34.x | Micrometer OTLP + CEL both ship the same protobuf line in the Boot BOM | platform/core | each Spring Boot minor |
| `kafka.version` **4.3.1** (`extra`) + explicit `kafka-clients` / `embedded-kafka_2.13` 4.3.1 | `ispf-server`, `ispf-driver-kafka` | Boot BOM forces 4.2.1 → `CompressionType` CNFE against EmbeddedKafka 4.3.1 | Spring Boot BOM moves to Kafka 4.3+ | drivers/messaging | each Spring Boot minor |
| `netty-bom` **4.2.17/4.2.18.Final** (`enforcedPlatform`) | `ispf-driver-opcua(-server)`, `ispf-driver-mqtt`, `ispf-driver-sparkplug-b`, `ispf-server` tests | Milo 0.6.x and Moquette 0.17 declare Netty 4.1.x; the bom lifts the whole graph to the patched 4.2 line (Dependabot CVE stream) | Milo / Moquette releases declare Netty 4.2 themselves; then drop the boms or collapse to one version | drivers/OPC UA | quarterly |
| `commons-beanutils` **1.11.0** (`constraints`) | `ispf-export-parquet` | hadoop-common 3.5.0 transitively pulls an older, vulnerable line | hadoop-common ≥ 3.5.1 declares ≥ 1.11 | platform/historian | each hadoop bump |
| `typescript` → `@typescript/typescript6` alias; `@typescript/native` = TS 7 | `apps/web-console/package.json` | `typescript-eslint` has no TS 7 API support yet (§5) | typescript-eslint supports TS 7 | web-console | each typescript-eslint major |

### 4. Heavy optional dependencies live in optional modules

A dependency that serves one feature and exceeds ~10 MB of transitive jars (Hadoop/Parquet, LibreOffice bridge, Cassandra driver, …) must sit in its own module or driver pack, wired via the existing pack/plugin mechanism and excluded from the default `bootJar`.

Done: **`ispf-export-parquet`** holds parquet-mr, Avro and hadoop-common. `ispf-server` sees only the `HistoryParquetExporter` SPI in `ispf-core` and discovers the module via `ServiceLoader`. It is included as `runtimeOnly` by default (no behaviour change) and dropped with `./gradlew bootJar -Pispf.exportParquet=false`; without it `GET …/history/export?format=parquet` returns **501** and the cold archive reports `skipped`. Next candidates: Cassandra `java-driver-core` (historian backend), POI (report rendering).

### 5. Frontend compiler split

`apps/web-console` keeps **TypeScript 7 for `tsc -b` and Vite** and **TypeScript 6 API (`@typescript/typescript6`) for ESLint** until `typescript-eslint` supports the TS 7.1+ API. The alias lives in `package.json` `devDependencies`; remove it when upstream catches up.

## Consequences

- Fewer surprise breakages for operators; upgrades to a new Boot/JDK major become a planned ISPF minor with a migration note.
- Slightly slower adoption of new language/framework features (typically one patch release).
- Existing pins become searchable technical debt with a removal condition instead of permanent config.
- `ispf-server` can be built without Parquet/Hadoop (`-Pispf.exportParquet=false`); default artifacts keep the module so existing deployments see no change.

## Related

- ADR-0022 driver production matrix (lab ≠ field honesty)
- [architecture](../architecture.md) § Stack
- Code analysis 2026-09 findings F-02, F-07
