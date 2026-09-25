# Changelog

All notable changes to the **ISPF platform** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
for the platform version in `gradle.properties` (`0.9.x` pre-1.0).

**Scope:** platform core, server, web-console, drivers, AI agent — not per-application
bundle changelogs (those live in each package manifest).

**Related history:** [roadmap](docs/en/roadmap.md) (phase journals),
[ADRs](docs/en/decisions/readme.md), [GitHub Releases](https://github.com/iot-solutions-ru/ispf/releases)
(tagged releases may lag `gradle.properties` version bumps).

Russian summary: [docs/ru/changelog.md](docs/ru/changelog.md).

## [Unreleased]

### Fixed

- **Binding expression errors** — `BindingExpressionEvaluator.evaluate` swallowed `ExpressionException` and returned empty, so a typo in a rule expression looked like "Expression returned empty" and left the target unchanged without failing the recalc. Uncomputable expressions now fail with the expression text and engine message; a blank expression still means nothing to evaluate.
- **Binding conditions** — `BindingRuleEngine.conditionPasses` treated an `ExpressionException` as `false`, so a typo or unknown name looked like a condition that honestly failed. Uncomputable conditions now fail the recalc with the object path and condition text. A computed `false` still skips the rule; a blank condition still means always run.
- **DEVICE delete leaves the driver running** — HTTP / UI / bulk delete removed the tree node but did not call `stopIfRunning`, so polls kept enqueueing for a path that no longer exists and `POST .../drivers/runtime/stop` failed with 404 while writing `driverStatus`. Delete now stops every active driver under the path (including children) before removing the node; `stop` / orphan polls no longer require the tree node.
- **Binding `call()`, `queryScalar()`, `queryRows()`, and `fire()`** — a failure inside a binding rule was returned as an empty result, so the rule looked successful and the target kept its previous value. Those operations now fail with the target and the original message. A nested `call()` while one is already running is rejected. A `fire()` that did run still counts as success when it has no payload.
- **Java function output schema** — a script function maps its result to `descriptor.outputSchema()`; a Java function returned the `DataRecord` the class built and ignored that schema. Callers could get fields and types that were not declared, or miss a required field the schema promised. The invoke path now projects the result onto the declared schema: extra fields are dropped, a missing required field fails the call, numbers are coerced like script output.
- **Binding rule chains** — `BindingRuleEngine` stopped after 8 passes or 16 nested activations with no error, so a long chain looked like a successful write while later fields stayed stale. The pass and depth limits are unchanged; hitting them now fails with the object path and "truncated".
- **`DOUBLE` fields** — `DataRecord` accepted `Double`, `Float`, and `Integer` but rejected `Long`, so a JSON integer larger than 32 bits failed with "must be double" while a smaller integer stored as `Integer` passed. Any finite number is now stored as `double`.
- **Java functions on `bootRun`** — saving `sourceType=java` failed with
  `classpath missing ispf-core (entries=1)` because Gradle puts one classpath jar on
  `java.class.path` and `JavaFunctionCompileClasspath` did not read its manifest
  `Class-Path`. The manifest entries are now included for `javac`.
- **`flexible` driver over UDP** — `exchangeUdpBytes` built the `DatagramPacket` with
  `InetSocketAddress.createUnresolved(...)`, which the JDK rejects (`IllegalArgumentException:
  unresolved address`), so every UDP exchange failed before the request left the host. Now resolves
  the address; covered by the new `FlexibleDeviceDriverTest` UDP loopback.
- **Marketplace install ACL** — local marketplace symbol / UI / analytics / bundle
  install and uninstall require a configurator role (developer/admin/tenant-admin);
  operators get 403.
- **Driver runtime point writeRoles** — `POST /drivers/runtime/write` honors the
  mapped point variable `writeRoles` (falls back to object `WRITE` when the
  variable is not materialised yet).
- **Automation object ACL** — `alert-rules`, `event-filters`, and `correlators`
  create/update/delete require object `WRITE`; get/list honor `READ` (+ tenant scope).
- **Data source / SQL binding object ACL** — `data-sources` and `sql-bindings` mutations
  (`create` / `update` / `execute-query` / binding `refresh`) require object `WRITE`;
  reads require `READ` (not only CONFIG role + tenant scope).
- **Driver runtime object ACL** — `configure` / `start` / `stop` / `write` /
  `catalog/import-points` require object `WRITE` (not only tenant scope + CONFIG
  role); `status` / `browse` / `poll` require `READ`. Shared catalog artifact
  mutate requires configurator.
- **Runtime settings save + restart** — System → Settings now writes sensitive
  keys (`ai.api-key`) instead of skipping them, rejects the `********` mask, and
  can schedule `ispf-server` restart (`POST /api/v1/platform/runtime-settings/restart`).
- **AI Studio chat nginx HTML 404** — OpenAI-compatible base URL without `/v1`
  (or a pasted `/chat/completions` path) posted to a host nginx and the chat
  showed the HTML 404 page. Host-only URLs now get `/v1`; gateway HTML is
  replaced with a hint plus the request URL.
- **Platform Runtime settings not applied** — Spring Boot 4 only loads
  `EnvironmentPostProcessor` from `META-INF/spring.factories`. ISPF registered
  the override loader in the Boot 3 `META-INF/spring/…EnvironmentPostProcessor`
  file, so **every** UI value in `runtime-settings.properties` (AI, database,
  messaging, drivers, cluster, …) never beat yaml/env defaults. Also drop the
  `local` profile lab LLM pin (`lab-edge.example.invalid`). HTTP errors now
  include exception type + URL; LLM client uses HTTP/1.1.
- **Auth logout AuthZ** — `POST /api/v1/auth/logout` is `permitAll` again so operators
  (and idempotent no-token calls) can revoke opaque Bearer sessions; was incorrectly
  CONFIG-gated. Evidence: [`docs/evidence/security-pentest/2026-09-09-engineering-review/`](docs/evidence/security-pentest/2026-09-09-engineering-review/).
- **Marketplace `mqtt-temperature` migrations** — aligned with `examples/lab-mqtt-temperature`
  H2-compatible SQL (`DOUBLE` / `TIMESTAMP` / PK syntax).
- **Modbus TCP/UDP/RTU `readConfig`** — prefer `DriverObject.configuration()` (keys from
  `driverConfigJson`) before device variables named `host`/`port`. Fixes lab bind where
  configure stored JSON but connect still used defaults `127.0.0.1:502`.

### Added

- **Web Console: two 2000+ line modules split into registries** (no behaviour change):
  `ispfSheetEval.ts` (2166 lines) is now a 45-line facade over `sheetEvalCore` /
  `sheetEvalTokenizer` / `sheetEvalParser` / `sheetEvalFunctions` (dispatcher) and six
  per-category function registries (`Logical`, `Math`, `Text`, `Date`, `Financial`, `Ispf`) —
  the 118-branch `invokeFunction` if-chain became `Record<string, SheetFunction>` tables;
  a characterization test replays 135 formulas captured from the pre-split implementation.
  `widgetEditorFields.tsx` (2492 lines) is now a 31-line dispatcher over
  `widgetTypeFields{Display,Chart,Data,Layout}.tsx` registries keyed by widget type (each
  renderer receives `WidgetFieldContextFor<K>` so the widget stays narrowed), plus
  `widgetFieldPrimitives` / `widgetRowNavigationFields` / `widgetDataSourceFields`; a test
  asserts the registry covers exactly the 43 types the old switch handled and mounts each.
- **Driver maturity evidence criterion (F-03)** — `DriverProductionMatrixTest` now enforces a
  mechanical bar for every `PRODUCTION` entry (`DriverMaturityEvidence`): ≥ 100 non-comment LOC of
  driver code, a `*Driver*Test` that drives the driver itself, and a verifiable peer (interop-lab
  fixture, in-process emulated peer, or transport-less driver). BETA entries that pass must be listed
  in `BETA_BY_DECISION` with a reason. `icmp`, `smb`, `wmi` demoted to **BETA** (156 PRODUCTION / 6
  BETA); `modbus-udp`, `flexible`, `webhook` got real loopback driver tests instead of parser-only
  coverage. `tools/driver-readiness-audit.py` output is now OS-independent (POSIX paths, stable pick).
- **Typed driver exceptions in the top-20 industrial drivers (F-05)** — all 204 `throw new
  DriverException(...)` sites in `virtual`, `mqtt`, `modbus-tcp/rtu/udp`, `opcua`, `opcua-server`, `snmp`,
  `bacnet`, `s7`, `http`, `flexible`, `iec104`, `iec104-server`, `dnp3`, `dlms`, `ethernet-ip`, `opc-da`,
  `opc-bridge`, `gps-tracker` now declare their `DriverErrorKind` (`DriverTransient` /
  `DriverConfiguration` / `DriverPermanent` / `DriverUnsupportedOperationException`), so
  `ispf.driver.errors.total{kind}` and driver status stop reporting `unclassified` for them.
  `DriverTypedExceptionsTest` guards the packs against regressions.
- **Error Prone on every `javac`** — `net.ltgt.errorprone` 5.1.1 / `error_prone_core` 2.50.0 across all Java
  modules; ERROR-severity bug patterns fail the compile. Eight patterns promoted from WARNING to ERROR
  after the sweep fixed every occurrence (`DefaultCharset`, `StreamResourceLeak`, `NonAtomicVolatileUpdate`,
  `OrphanedFormatString`, `ArgumentSelectionDefectChecker`, `AlreadyChecked`, `DuplicateBranches`,
  `MissingOverride`); fixes include MQTT publish / Basic-auth / DLMS bytes no longer depending on the JVM
  default charset, unclosed `Files.walk` streams, racy `volatile` counters in test fixtures and dead
  conditions in `PlatformUserService` / `EventCorrelatorService`. `-Pispf.errorprone=false` for local quick
  iteration; see [testing § Static analysis](docs/en/testing.md#static-analysis-error-prone).
- **Nightly dependency vulnerability gate** — `./gradlew cyclonedxBom` produces one CycloneDX SBOM over
  every module's `runtimeClasspath`; the `dependency-vulnerabilities` nightly job scans it with Trivy and
  fails on fixable CRITICAL/HIGH (full report + SBOM as artifact). First run remediated: Tomcat 11.0.26,
  Netty 4.2.18 for the Neo4j driver, Bouncy Castle 1.86, lz4-java 1.11.3, commons-configuration2 2.15.1 —
  all registered in the ADR-0059 pin registry with removal conditions.
- **ObjectManager path-sync benchmark + `synchronized` audit (F-08)** —
  `ObjectManagerPathSyncBenchmarkTest` replays 8 followers × 20 syncs on distinct paths against the
  pre-F-08 instance monitor and the current RW-lock + per-path monitor: **8.2× faster** (402.9 → 49.0 ms
  with a 2 ms simulated DB round-trip); the test asserts ≥ 2× so the split cannot silently regress. All 76
  remaining `synchronized` sites in `ispf-server` reviewed — no other instance-wide monitor on a hot path;
  one watch item (`RecentEventCache` read scans). Report:
  `docs/evidence/quality/2026-09-20-synchronized-audit.md`.
- **Large server services split into focused collaborators** (no API change):
  `ApplicationBundleDeployService` 1459 → 674 lines — tree artifacts loop in
  `BundleTreeArtifactsApplier` (+ `BundleApplyLog`), single-artifact upserts in
  `BundleArtifactDeployers`, operator-UI derivation/persistence in `BundleOperatorUiSync`;
  `WorkflowService` 1329 → 996 — BPMN node execution in `WorkflowTaskExecutor`, instance
  snapshot/event projection in `WorkflowInstanceStatePublisher`, and the five copies of
  the "load waiting instance → engine step → save/publish/fail/notify parent" sequence
  collapsed into `resumeWaitingInstance` / `finishStep`; `ReportService` 1183 → 830 —
  `ReportSqlQuery` (SELECT guard, placeholder binding), `ReportTableExport`
  (CSV/HTML/XLSX/XLS, one workbook writer), `TreeVariablesReportRows`. New unit tests:
  `WorkflowTaskExecutorTest`, `ReportTableExportTest`.
- **Web Console lint gate at zero warnings** — `npm run lint` now runs with
  `--max-warnings 0` (was 180). All `react-hooks/exhaustive-deps`,
  `@typescript-eslint/no-non-null-assertion` and `react-refresh/only-export-components`
  findings are fixed: `!` assertions replaced by `required()` / explicit guards, hook
  dependencies corrected, and non-component exports (context hooks, helpers, catalogs)
  moved to sibling `.ts` modules (`useAdminFocus`, `useAgentChat`, `useDashboardContext`,
  `useTheme`, …) so Fast Refresh keeps component state.
- **Optional Parquet export module** — parquet-mr / Avro / hadoop-common moved from
  `ispf-server` into `packages/ispf-export-parquet` (SPI `HistoryParquetExporter` in
  `ispf-core`, discovered via `ServiceLoader`). Default `bootJar` still includes it;
  `./gradlew bootJar -Pispf.exportParquet=false` builds a slimmer server where
  `GET …/history/export?format=parquet` answers **501** and the cold archive run
  reports `skipped` (ADR-0059 §4).
- **JaCoCo coverage gate** — `./gradlew coverageVerify` (CI pr-fast backend job) fails when a
  module drops below its LINE/BRANCH floor from `coverageFloors` in the root `build.gradle.kts`
  (`ispf-core`, `ispf-expression`, `ispf-plugin-*`, `ispf-server`, `ispf-ai-agent`). Floors sit a
  few points under the 2026-09 baseline and are ratcheted up, never down.
- **Batch function invoke** — `POST /api/v1/objects/by-path/functions/invoke-batch`
  (≤100 items, per-item ACL). Operator alarm bar **Acknowledge all** uses one HTTP call
  instead of N× `acknowledgeAlarm`.
- **Operator tree search** — Web Console object tree search queries the full tree
  (`GET /api/v1/objects/search`), not only already-expanded folders (#204).
- **CEL on event journal feed** — event journal list can filter with CEL expressions (#205).
- **Chart historian granules** — auto chart buckets align with historian rollup granules (#207).
- **OPC UA Sign / SignAndEncrypt** — Milo client/server security policies with PKI trust
  lists; discovery endpoint stays None for GetEndpoints; lab default remains None (#208).
- **Event journal batch purge** — `POST /api/v1/events/journal/purge` deletes stored history
  in one store statement (JDBC / ClickHouse); Cassandra returns 409 (#211).
- **Internal engineering security review (2026-09-09)** — defensive lab surface checks +
  AuthN/AuthZ/ACL/tenant/MFA code review; **not** a hired pen-test and **does not** close G-01.
- **OT Trust Wave 3b** — +7 clean-room codecs: `ocpp`, `odata`, `grpc` (JSON-lab), `openadr`, `scpi`, `visa` (SOCKET-only), `knx-tp`.
- **Enterprise L catalog tooling** (tracked under `tools/historian-scale/`): seed 50k
  history-enabled devices, `GET /api/v1/platform/analytics/history-enabled-count`, and
  analytics-scale gate counting **history-enabled variables** (not binding-rule `/tags`).
- **HMI offline lab soak** — `npm run pwa:offline-field-soak` (Playwright CDP offline, configurable
  duration; demostand **2 h** evidence archived for Post-S33).
- **OT Trust Wave 2 codec promotion** — `redis`, `mitsubishi-slmp`, `yaskawa-memobus`, `sparkplug-b`
  promoted STUB → **PRODUCTION** with real codecs + in-process loopback tests (fake RESP / SLMP 3E /
  Modbus TCP / Moquette+Sparkplug protobuf); matrix `POLL_WRITE`; evidence
  [`docs/evidence/ot-trust/2026-09-05-wave2-codec-promotion.md`](docs/evidence/ot-trust/2026-09-05-wave2-codec-promotion.md).

### Docs

- **Quality hold pack** — G-01 vendor outreach draft, Web Console dogfood checklist, ACL mutate-API audit note. Links from [parked-backlog](docs/en/parked-backlog.md) **P-PENTEST**. Prep ≠ pen-test pass.
- **MQTT wire honesty** — Paho mqttv3 = MQTT **3.1.1**; MQTT 5 documented as a non-goal (#209).
- **Nested dashboard inheritContext** — nested dashboards honor inheritContext (#206).
- **External AI IDE / hosted SPA** — how-to for Cursor/VS Code: MCP, BFF invoke, Vite `base`, ui-pack zip. Hub [`docs/en/external-ide.md`](docs/en/external-ide.md) (RU [`docs/ru/external-ide.md`](docs/ru/external-ide.md)); P7/P9, agent-knowledge approach **I**, marketplace, solution-developer-guide.
- **OT Trust BL-140 lab soak pull 2026-09-10…14** — P1/P2/P3 timer JSON all `pass: true` / RUNNING via jump+`ispf_lab_ed25519`; supersedes Cloud Agent SSH blocker for [#212](https://github.com/iot-solutions-ru/ispf/pull/212). Lab ≠ field / not OT 10/10. Evidence [`docs/evidence/ot-trust/2026-09-14-bl140-lab-soak-pull.md`](docs/evidence/ot-trust/2026-09-14-bl140-lab-soak-pull.md).
- **OT Trust BL-140 lab soak stopped** — operator stop 2026-09-14; P-OT marked Consumed (lab); timers cleaned. Not field Done / not OT 10/10. Evidence [`docs/evidence/ot-trust/2026-09-14-bl140-lab-soak-stopped.md`](docs/evidence/ot-trust/2026-09-14-bl140-lab-soak-stopped.md).
- **OT Trust BL-140 Pilot #1 soak day 2** — auto soak-check 2026-09-07 06:00 MSK pass (`RUNNING`, 50 tags). Evidence [`docs/evidence/ot-trust/2026-09-07-bl140-pilot1-day2.md`](docs/evidence/ot-trust/2026-09-07-bl140-pilot1-day2.md).
- **OT Trust BL-140 Pilot #1 C5 + daily soak check** — disconnect/reconnect on `lab-ot-vlan-192.168.100` (`ERROR`/`Not connected` → `RUNNING`); `tools/ot-trust/pilot1-modbus-soak-check.py` + lab systemd timer 06:00 MSK. Not field Done / not OT 10/10. Evidence [`docs/evidence/ot-trust/2026-09-06-bl140-pilot1-c5-disconnect.md`](docs/evidence/ot-trust/2026-09-06-bl140-pilot1-c5-disconnect.md).
- **OT Trust BL-140 Pilot #1 lab day 1** — named site `lab-ot-vlan-192.168.100`; Modbus peer `:1502`; ISPF device 50 tags RUNNING; write+historian §1 green; soak journal day 1. Not field Done / not OT 10/10. Evidence [`docs/evidence/ot-trust/2026-09-06-bl140-pilot1-lab-day1.md`](docs/evidence/ot-trust/2026-09-06-bl140-pilot1-lab-day1.md).
- **OT Trust BL-140 Pilot #1 kickoff** — Modbus plant field-prep pack: C1–C6 checklist + site intake, empty soak journal instance, playbook links. Evidence [`docs/evidence/ot-trust/2026-09-06-bl140-pilot1-kickoff.md`](docs/evidence/ot-trust/2026-09-06-bl140-pilot1-kickoff.md).
- **OT Trust BL-141 GPS tracker fixture** — NMEA TCP listener stand-in + feed→`/last` smoke (compose peers **14/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-gps-tracker-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-gps-tracker-fixture.md).
- **OT Trust BL-141 Modbus RTU fixture** — RTU ADU (unit+PDU+CRC16) over TCP lab bridge + FC6/FC16→FC3 smoke (compose peers **13/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-modbus-rtu-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-modbus-rtu-fixture.md).
- **OT Trust BL-141 S7 SoftPlc fixture** — `fbarresi/softplc` ISO-on-TCP `:102` + REST seed/verify DB1 REAL @80 (compose peers **12/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-s7-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-s7-fixture.md).
- **OT Trust BL-141 DNP3 fixture** — stdlib TCP outstation + integrity-poll smoke (compose peers **11/20**, poll-only ADR-0057). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-dnp3-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-dnp3-fixture.md).
- **OT Trust BL-141 Modbus UDP fixture** — stdlib MBAP-over-UDP peer + FC6/FC16→FC3 smoke (compose peers **10/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-modbus-udp-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-modbus-udp-fixture.md).
- **OT Trust BL-141 DLMS fixture** — stdlib TCP WRAPPER peer + SET/GET REGISTER smoke (compose peers **9/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-dlms-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-dlms-fixture.md).
- **OT Trust BL-141 EtherNet/IP fixture** — stdlib CIP UCMM peer + Write/Read Tag DINT smoke (compose peers **8/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-ethernet-ip-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-ethernet-ip-fixture.md).
- **OT Trust BL-141 IEC 104 fixture** — stdlib IEC 60870-5-104 TCP outstation + C_SE_NC_1/C_RD_NA_1 smoke (compose peers **7/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-iec104-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-iec104-fixture.md).
- **OT Trust BL-141 BACnet fixture** — stdlib BACnet/IP UDP lab agent + Read/WriteProperty smoke (compose peers **6/20**). Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-bacnet-fixture.md`](docs/evidence/ot-trust/2026-09-06-bl141-bacnet-fixture.md).
- **OT Trust BL-141 fixture depth** — SNMP + HTTP lab docker fixtures + smoke (compose peers **5/20**); stdlib agents; self-tests without docker. Evidence [`docs/evidence/ot-trust/2026-09-06-bl141-snmp-http-fixtures.md`](docs/evidence/ot-trust/2026-09-06-bl141-snmp-http-fixtures.md).
- **OT Trust post-merge honesty** — after Waves 1–11 / catalog **162/162** (#144): lab ≠ field certification; TOP-20 docker fixture/smoke inventory (3/20 compose peers); next depth = BL-141 fixtures + plant soaks, not more stubs. Evidence [`docs/evidence/ot-trust/2026-09-06-post-merge-lab-vs-field.md`](docs/evidence/ot-trust/2026-09-06-post-merge-lab-vs-field.md).
- **OT Trust Wave 3 — 13 clean-room codec promotions** — `beckhoff-ads`, `mitsubishi-melsec`, `iec62056`, `ieee2030-5`, `mqtt-sn`, `nats`, `pulsar` (lab framing), `onvif`, `mtconnect`, `knx`, `lwm2m`, `websocket`, `graphql` → PRODUCTION with loopback tests; Apache-2.0 / JDK-only; high-risk stacks untouched; evidence `docs/evidence/ot-trust/2026-09-05-wave3-codec-promotion.md`.
- **OT Trust — raise readiness for all 162 packs** — stub-kit **v0.2** (lab loopback write) +
  contract tests for catalog stubs → audit label **`STUB_LAB`** (honest: still no protocol codec);
  `tools/driver-stubs/raise-stub-readiness.py`; WRITE_UNDERCLAIM false-positives for ingress read-only drivers fixed.
- **OT Trust — full 162-driver readiness audit** — `tools/driver-readiness-audit.py` + report
  [`docs/evidence/ot-trust/driver-readiness.md`](docs/evidence/ot-trust/driver-readiness.md)
  (matrix honesty: promote `email`/`sms`/`webhook`/`smb`, claim `smpp` WRITE; CI gate on interop workflow).
- **OT Trust Wave 1 hardening** — writable Modbus lab fixture (`deploy/driver-interop/modbus/server.py`) replaces `oitc/modbus-server` (FC6 reset in CI); smoke covers FC6+FC16 + optional OPC UA write; writePoint↔capability source gate; lab day-1 journal.
- **OT Trust Wave 1 kickoff** — unpark P-OT; [ADR-0057](docs/en/decisions/0057-ot-trust-wave1-dnp3-poll-only.md) (DNP3 PRODUCTION poll-only); `http` WRITE in production matrix; evidence `docs/evidence/ot-trust/`.
- **G-01 pen-test prep (deeper+)** — RoE [pen-test-roe.md](docs/en/pen-test-roe.md), vendor questionnaire, expanded case catalog (+ audit/export), case-results/evidence-index templates, preflight with OpenAPI status + security-header capture; operator [pen-test-prep.md](docs/en/pen-test-prep.md).
- **G-01 pen-test SOW** — [pen-test-scope.md](docs/en/pen-test-scope.md); evidence folder `docs/evidence/security-pentest/`.
- **ADR-0056** — WebAuthn / IdP OTP MFA follow-up (BL-194) **Proposed** (implementation still parked).
- **Parked backlog board** — [docs/en/parked-backlog.md](docs/en/parked-backlog.md) (OT Trust Wave 1 **in progress**; live ERP / WebAuthn / pen-test / field tablet stay parked; Enterprise L lab PASS noted).
- **Enterprise L lab evidence** (2026-09-05, `192.168.100.10`): 50k history-enabled + ≥1B CH rows + multi-tag p95≈49 ms — `docs/evidence/historian-scale/`.
- Enterprise L playbook paths restored to `tools/historian-scale/` (seed/count/gates).
- Historian JVM scale gate archive (`docs/evidence/historian-scale/`).
- HMI offline 2h lab journal + JSON under `docs/evidence/hmi-offline/`.

## [0.9.207] - 2026-09-01

### Fixed

- **SQL statement splitting** — migration and seed scripts split on `;` within single-line
  batches (respects quoted literals); fixes H2 CI failures on multi-statement bundle SQL.
- **Example bundle H2 compatibility** — `lab-mqtt-temperature` and `mini-tec` migrations use
  `DOUBLE` / `TIMESTAMP` instead of Postgres-only types.
- **Bundle deploy suite** — import step requires strict `OK` status again (no PARTIAL soft-pass).

### Docs

- Scorecard demostand pin **0.9.207**.

## [0.9.206] - 2026-09-01

### Fixed

- **Migration applyPending scope** — bundle deploy applies pending migrations for the
  target data source only (fixes cross-app pollution when versions collide on H2 CI).
- **Compensating cleanup (H1-full)** — failed/partial deploys that do not activate a
  snapshot best-effort remove manifest tree paths (first install) or bundle visual
  groups only (when a prior active snapshot exists).

### Docs

- Scorecard demostand pin **0.9.206**.

## [0.9.205] - 2026-09-01

### Fixed

- **Bundle deploy honesty (H1-lite)** — deploy status is `OK` / `PARTIAL` / `FAILED`
  (no silent force-`OK`); marketplace install and AI `deploy_step_import` preserve
  that status; failed/partial imports do not mark the playbook step complete.
- **Active snapshot only on clean deploy** — `recordDeployment` runs after
  `syncApplicationTree`; PARTIAL/FAILED attempts are audited with
  `is_active=false` and do not clear the previous `findActive()` row.

### Docs

- Scorecard demostand pin **0.9.205**.

## [0.9.204] - 2026-08-31

### Fixed

- **Periodic binding consecutive failures** — after N RuntimeExceptions (default 5,
  `ispf.binding.periodic.max-consecutive-failures`) delete schedule row, skip in-JVM,
  and persist `enabled=false` on `@bindingRules` when possible.

### Docs

- HMI live FPS re-smoke on demostand **0.9.203**: `ui-pump-station` median 60 FPS,
  517 Object WS `VARIABLE_UPDATED` (`docs/evidence/hmi-fps/`).

## [0.9.203] - 2026-08-31

### Fixed

- **Workflow trigger index prune** — stale workflow paths removed from
  `WorkflowEventTriggerIndex` after ObjectNotFound soft-fail (C2 follow-on).
- **SQL binding orphan auto-disable** — missing target disables tree/app SQL
  bindings once (H4 parity with alert orphan disable-once).

### Docs

- Scorecard demostand pin **0.9.202**; CEL verify + AI HVAC/MES/SCADA soft re-soak
  evidence archived for 0.9.202.

## [0.9.202] - 2026-08-31

### Fixed

- **Analytics scheduler retry loop (C1)** — failed due-tag evaluation now `markRan`
  with error instead of staying perpetually due.
- **Workflow trigger soft-fail (C2)** — stale trigger index entries no longer abort
  sibling variable/event workflow fan-out.
- **Periodic binding schedule advance (H3)** — `RuntimeException` no longer advances
  `next_run_at` (matches ObjectNotFound orphan path).
- **Orphan alert disable-once (H4)** — in-memory guard stops 1 Hz WARN spam when
  disable succeeds or fails after first missing-target hit.
- **Process program ObjectNotFound (H7)** — missing target logs WARN + records cycle.
- **Analytics quality propagation (H6)** — per-tag soft-fail on missing objects.
- **Boot startup order (H8)** — driver auto-start and index rebuilds run after
  `PlatformObjectReadinessGate.markObjectTreeReady` (safe order constants).
- **Metrics probe / derived-tag idle gates** — no tree writes before initialized.

### Changed

- **Corrupt roles JSON (H2)** — `deserializeRoles` paths log WARN (behavior unchanged).
- **Agent session turns (H5)** — `@Transactional` on `AgentSessionRepository.saveTurn`.
- **Platform scheduler failures** — tree/legacy schedule action errors now log WARN.

## [0.9.201] - 2026-08-30

### Fixed

- **Scheduler idle gates** — platform schedules, process programs, workflow retry,
  and workflow cron wait for `ObjectManager.isInitialized()` before work.
- **Orphan alert auto-disable** — missing watch target / ALERT node disables the
  rule once instead of WARN-spamming every poll tick.

### Changed

- **Platform metrics** — `objectTree.ready` in `/api/v1/platform/metrics` snapshot;
  docs list Prometheus `ispf.object_tree.ready`.

## [0.9.200] - 2026-08-30

### Fixed

- **Self-diagnostics bootstrap** — probe variable zero values no longer use a
  Java ternary that promotes `0` to `Double` (INTEGER DataRecord rejected;
  demostand skipped seeding `platform-metrics-probe` vars + dashboard).

## [0.9.199] - 2026-08-30

### Fixed

- **Alert rule soft-fail / idle gate** — missing watch target or ALERT node no longer
  aborts the periodic poll / variable-change fan-out; scheduler waits for
  `ObjectManager.isInitialized()`.
- **Periodic binding fireDue soft-fail** — `ObjectNotFound` removes the stale
  `platform_binding_periodic_rules` row instead of stopping the tick.

### Added

- **Prometheus** — `ispf.object_tree.ready` gauge (0/1 from `ObjectManager.isInitialized()`).

## [0.9.198] - 2026-08-30

### Fixed

- **Analytics tag catalog boot race** — do not scan `@bindingRules` until
  `ObjectManager` is initialized (stops early WARN ObjectNotFound spam).

## [0.9.197] - 2026-08-30

### Fixed

- **Historian / periodic binding discovery** — `@bindingRules` path scan joins
  `object_nodes` so orphan variables for deleted objects no longer WARN.
- **SQL binding delete cascade** — deleting a tree object disables matching
  application + tree SQL bindings targeting that subtree.
- **AI agent UTF-8 mojibake** — operator-visible Russian/punctuation literals
  restored in agent services.
- **Prometheus** — `ispf.websocket.clients` gauge for open Object WS sessions.

## [0.9.196] - 2026-08-30

### Fixed

- **Report tree-variables MEMBER ACL** — interactive report/export/agent runs omit
  per-variable ACL-denied rows (`VariableMemberAccessService`); report API paths
  require object read/write. CEL demostand verify smoke archived for 0.9.195.

## [0.9.195] - 2026-08-30

### Fixed

- **Scheduler idle gate** — periodic binding / application SQL binding / analytics
  schedulers skip work until `ObjectManager` is initialized (avoids early-boot
  `ObjectNotFoundException` ERROR spam before the object tree is ready).
- **SQL binding soft-fail** — missing target object or variable on refresh logs a
  warning and skips the binding instead of failing the scheduler tick
  (`SqlBindingObjectService`, `ApplicationSqlBindingService`).
- **Legacy user object path** — `PlatformUserObjectTreeService.syncUser` migrates
  exact `root.users.<username>` to `root.platform.security.users.<username>`
  before ensuring the tree node (fixes `dogfood-deploy` sync ERROR).

## [0.9.194] - 2026-08-30

### Fixed

- **WebSocket `/ws/objects` handshake** — local profile `issuer-uri` placeholder
  (`example.invalid`) made lazy `JwtDecoder` resolution throw
  `IllegalStateException` / `UnknownHostException` outside the `JwtException`
  catch, flooding demostand logs with `HandshakeFailureException`. Opaque
  platform tokens skip JWT decode; decoder resolution failures return false
  instead of failing the handshake hard.

### Documentation / prior Unreleased

- **CI nightly Invalid workflow** — `run` + `uses` were merged on the BL-180
  upload step (since 2026-08-24), so GitHub rejected the file (0-job failures;
  schedules stalled). Split the step; workflow file is `nightly.yml` with
  `push-ack` and heavy jobs gated `if: event != push`.
- **HMI live FPS gate** — unmocked demostand path opens a real operator mimic
  (`E2E_OPERATOR_APP`, default `ui-pump-station`), requires Object WS
  `VARIABLE_UPDATED`, writes optional evidence JSON (`E2E_LIVE_FPS_EVIDENCE`).
- Demostand **0.9.193** `ui-pump-station` live FPS: median **60**, 479 WS updates
  (`docs/evidence/hmi-fps/2026-08-30-ispf-vps-0.9.193-ui-pump-station.json`).

## [0.9.193] - 2026-08-30

### Fixed / Changed

- **License PEM** — shared `LicensePublicKeySupport.parsePrivateKey` for bundle + analytics-pack signing (literal `\n` / single-line forms); round-trip unit test.

### Documentation

- Post-S33 scorecard / tender honesty (pin **0.9.192**, AI re-soak **0.9.191**, G-03 RLS, ADR-0055 expression-language + verify smoke).
- Security tenancy/RLS + G-08; HMI offline CI baseline reaffirm.

### Changed

- Platform version bump to **0.9.193**.

## [0.9.192] - 2026-08-30

### Fixed

- **License PEM env loading** — signing/verify tolerate literal `\n` sequences
  left by systemd `EnvironmentFile` / dotenv (was `Illegal base64 character 5c`
  on AI live `apply:true`). Demostand enable script writes single-line PEMs.

### Evidence

- BL-180 soft re-soak on demostand **0.9.191**: HVAC/MES/SCADA
  `functionalOk` + `softBudgetMet` (~19s each), `bundleTrust=signed`
  (`docs/evidence/ai-generator/2026-08-30-ispf-vps-0.9.191-*`).

### Changed

- Platform version bump to **0.9.192**.

## [0.9.191] - 2026-08-30

### Fixed

- **BL-154 Object Query / function / binding residual** — interactive HTTP and
  agent function invocation plus binding evaluation run in `MEMBER` mode; OQ
  live/historian projections, variable introspection, expands, platform ref, and
  application-script reads omit variables denied by `readRoles`; platform ref
  writes reject denied updates. Background scheduler / binding-engine automation
  remains `SYSTEM`.
- Platform backup remains **admin-only** (regression test + docs honesty).

### Changed

- Platform version bump to **0.9.191**; AI context pack refresh.
- CI nightly no longer runs on every `main` push (schedule + `workflow_dispatch`
  only) to restore usable gate signal.

## [0.9.190] - 2026-08-30

### Fixed

- **BL-154 trusted-channel close** — per-variable ACL (`MEMBER` via
  `VariableMemberAccessService`) on analytics query/export/expression, agent
  history/analytics tools, WebSocket delivery, object editor, expression
  evaluate, and Haystack/Brick semantic export/query.
- **Federation tunnel on-behalf-of** — edge installs delegated principal;
  anonymous value/history/invoke denied (health probes stay channel-only).
- **Federation HTTP peer on-behalf-of (G-05)** — hub sends
  `X-ISPF-On-Behalf-Of-User` / `Roles` / `Tenant`; peer
  `FederationOnBehalfOfFilter` applies channel ∩ claimed roles (no privilege
  escalation) before `MEMBER` enforcement.
- **Hub federated proxy fail-closed** — omit remote-only variables without local
  ACL metadata; non-admin proxy write requires local variable definition.

### Changed

- Platform version bump to **0.9.190**; AI context pack refresh for the release.
- Security / federation / compliance docs and scorecard post-audit note updated
  (frozen 0.9.102 Security score unchanged until next full audit).

## [0.9.189] - 2026-08-30

### Added

- **CEL formal verification (product gate, ADR-0055)** — Z3-backed checks for boolean
  conditions (unsatisfiable / tautology), equivalence proofs, runtime settings
  (`ispf.expression.formal-verification.*`), REST
  `POST /api/v1/expressions/verify` and `/verify-equivalence`, AI tool
  `verify_cel_condition`, and enforcement on alert/binding apply (human REST + AI).
- **Workflow BPMN design-time formal gate** — sequence-flow conditions verified on
  `saveBpmn` and on activate (`ACTIVE`).
- **Historian helper formal rewrite** — `avg`/`min`/`max`/`last`/`sum`/`live` calls
  map to correlated `self.__histN` placeholders for SMT (template-level, not sample
  expansion).
- **97 protocol catalog stub drivers** as individual `ispf-driver-<id>` packs
  (Apache-2.0, `STUB` maturity, shared `ispf-driver-stub-kit`), bringing the
  documented pack catalog to **162** entries ([drivers](docs/en/drivers.md)).
- Root **Keep a Changelog** (`CHANGELOG.md`) + Russian summary (`docs/ru/changelog.md`).

### Changed

- **CEL** `dev.cel:cel` **0.13.1 → 0.14.0** (formal verifier available; Program Planner path).
- **protobuf-java** aligned to **4.36.0** (root force + Spring Boot BOM override) so
  CEL 0.14 gencode and runtime stay compatible.
- Dependabot upgrades on `main`: Spring Boot **4.1.1**, Gradle Wrapper **9.7.1**,
  Jackson, Avro, SMBJ, JSch, Docker Buildx action, and web-console npm bumps
  (antd, framer-motion, testing-library, …).
- **`@vitejs/plugin-react` → 6.1.0** with `apps/web-console/.npmrc`
  `legacy-peer-deps=true` (Babel peer conflict with Vite 8 / Rolldown).
- AI **context pack** generator parses the full Complete/`Полный каталог` driver table
  (maturity + license columns).
- Platform version bump to **0.9.189**.

### Fixed

- Flyway **V89** `random_uuid` DDL skipped on H2 via existing `${rls_block_*}` placeholders
  (PostgreSQL-only body), unblocking Spring tests after CEL/protobuf work.
- Context pack regeneration no longer collapses to the maturity summary table (~23 rows).

## [0.9.188] - 2026-08-25

### Added

- VPS helper and evidence for **signed bundles** gate
  (`deploy/tools/vps-enable-signed-bundles.sh`, evidence journals).
- Post-S33 / MES GA smoke evidence on VPS; PostgreSQL `random_uuid` compat migration (V89).

### Fixed

- Bundle license verification of `contentSha256` against **raw JSON** (signed-bundles path).

### Changed

- Platform version bump to **0.9.188**; AI context pack refresh for the release.

[Unreleased]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.193...HEAD
[0.9.193]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.192...v0.9.193
[0.9.192]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.191...v0.9.192
[0.9.191]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.190...v0.9.191
[0.9.190]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.189...v0.9.190
[0.9.189]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.188...v0.9.189
[0.9.188]: https://github.com/iot-solutions-ru/ispf/releases/tag/v0.9.188
