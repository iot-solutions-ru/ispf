> **Language:** Canonical English. Russian edition: [ru/testing.md](../ru/testing.md).

# Platform Testing and Quality Gates

> **Status:** Stable — unit, integration, golden-path regression, coverage floors, and static analysis gates. Status tags: [doc-status.md](doc-status.md).

This guide explains how to run automated tests and code quality checks when developing or verifying changes in the ISPF repository.

---

## 1. Quick Test Commands

> **Tip for fast local verification:** run `testPrFast` (or `./tools/ci/pr-fast.sh` / `.\tools\ci\pr-fast.ps1`) before opening a PR rather than running `./gradlew test` across all 160+ driver modules at once.

```bash
# Fast pre-PR backend suite (same as CI pr-fast)
./gradlew testPrFast \
  -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev

# Run a single test class
./gradlew :packages:ispf-server:test --tests "com.ispf.server.api.DashboardApiTest" \
  -Dispf.test.skipLoad=true

# End-to-end golden-path regression suite across all core engines
./gradlew :packages:ispf-server:test --tests "com.ispf.server.regression.*" \
  -Dispf.test.skipLoad=true

# Web Console unit tests and production TypeScript build
cd apps/web-console
npm test
npm run build
```

Backend test stack: **JUnit 5**, **Java 25**, **Spring Boot 4** (`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`), and Jackson 3 (`tools.jackson`). In the `test` profile (`application-test.yml`), tests run against an in-memory H2 database with Flyway migrations applied and external MQTT/NATS brokers disabled by default.

---

## 2. Key Test Suites by Layer

| Layer / Module | Key Test Classes | What They Verify |
|----------------|------------------|------------------|
| `ispf-core` & `ispf-expression` | `ObjectTreeTest`, `DataRecordTest`, `ExpressionEngineTest`, `BindingEvaluatorTest` | Object tree invariants, CEL evaluation, formal Z3 verification, and built-in signal functions |
| `ispf-plugin-blueprint` & `workflow` | `BlueprintEngineTest`, `BpmnParserTest`, `WorkflowEngineV2Test`, `WorkflowEngineV3Test` | Blueprint inheritance/parameterization and BPMN 2.0 subset execution |
| `ispf-driver-*` | `*DeviceDriverTest` (see [Driver interop lab](driver-interop-lab.md)) | Loopback protocol read/write tests for all production drivers |
| `ispf-server` Golden Path | `PlatformGoldenPathRegressionTest`, `PlatformChangeSetAndBackupRegressionTest`, `PlatformWebSocketLiveRegressionTest` | End-to-end verification of `SINGLETON`/`INSTANCE` blueprints, strict bundle deploy, BFF + `sqlBinding`, CEL + `@dashboardContext`, Historian + Analytics, and Alert → Correlator → BPMN → Work Queue |
| Solution Bundle Tests | `FunctionTestRunnerTest`, `ExamplesBundleValidationTest` | Declarative `tests[]` inside `bundle.json` (executed in a rollback-only transaction on deploy) and strict validation of all reference bundles in `examples/` |
| Documentation & Matrix Gates | `AdrHonestyGateTest`, `DriverProductionMatrixTest` | Verifies that ADR status tables and `docs/en/drivers.md` maturity counts match the actual codebase |

---

## 3. Cluster, Coverage, Static Analysis, and Vulnerability Gates

### Cluster Ownership and Failover
```bash
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.driver.DriverOwnershipServiceTest \
  --tests com.ispf.server.driver.ClusterFailoverIntegrationTest
```

### Code Coverage Gate (JaCoCo)
Minimum line and branch coverage floors are enforced per module in the root `build.gradle.kts` (`coverageFloors`):
```bash
./gradlew coverageVerify
./gradlew coverageReport
```

### Compile-Time Static Analysis (Error Prone)
Every Java compilation runs [Error Prone](https://errorprone.info/) with bug patterns (`DefaultCharset`, `StreamResourceLeak`, `NonAtomicVolatileUpdate`, `OrphanedFormatString`, `MissingOverride`, etc.) promoted to compile-time `ERROR`. For a quick local run without Error Prone, pass `-Pispf.errorprone=false`.

### Dependency Vulnerability Audit (CycloneDX + Trivy)
```bash
./gradlew cyclonedxBom -Pispf.errorprone=false
trivy sbom build/reports/cyclonedx/bom.json
```
Version pins and CVE remediation rules are tracked in [ADR-0059](decisions/0059-toolchain-version-policy.md).
