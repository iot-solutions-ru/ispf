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

### 4. Heavy optional dependencies live in optional modules

A dependency that serves one feature and exceeds ~10 MB of transitive jars (Hadoop/Parquet, LibreOffice bridge, Cassandra driver, …) must sit in its own module or driver pack, wired via the existing pack/plugin mechanism and excluded from the default `bootJar`. First target: `ispf-export-parquet` (Parquet/Avro/Hadoop out of `ispf-server`).

### 5. Frontend compiler split

`apps/web-console` keeps **TypeScript 7 for `tsc -b` and Vite** and **TypeScript 6 API (`@typescript/typescript6`) for ESLint** until `typescript-eslint` supports the TS 7.1+ API. The alias lives in `package.json` `devDependencies`; remove it when upstream catches up.

## Consequences

- Fewer surprise breakages for operators; upgrades to a new Boot/JDK major become a planned ISPF minor with a migration note.
- Slightly slower adoption of new language/framework features (typically one patch release).
- Existing pins become searchable technical debt with a removal condition instead of permanent config.
- `ispf-server` bootJar shrinks once Parquet export moves out (tracked as a follow-up).

## Related

- ADR-0022 driver production matrix (lab ≠ field honesty)
- [architecture](../architecture.md) § Stack
- Code analysis 2026-09 findings F-02, F-07
