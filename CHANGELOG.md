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

### Changed

- **Solution canon in bundle validation** — `LOGIC_HOST_DEVICE` (a function hosted on a DEVICE, or a SINGLETON blueprint with `targetObjectType=DEVICE`) was an ERROR, so validate/deploy returned `status: ERROR` and the agent judge sent a working solution back to REWORK. It is now a WARNING that names the canonical host (SINGLETON hub or INSTANCE twin) and does not block deploy. Bundles under `examples/` still must carry no `LOGIC_HOST_DEVICE` (`ExamplesBundleValidationTest`). ADR-0060 W1 amended.
- **Mixin attachments in Web Console** — the mixin editor Actions block lists objects from `GET /api/v1/blueprints/attachments` (filtered by the current blueprint), including empty “not applied anywhere”, click-to-select path, `attachedAt`, and merge warnings. `/instances` stays on INSTANCE Upgrade only. Object inspector Applied blueprints enrich MIXIN rows with `attachedAt` / warnings via `?objectPath=`.
- **Object path fields** — screens that asked for an existing object path as plain text now use the tree picker (`ObjectPathField`): mixin Apply and instantiate parent (with “use selected object”), schedules, SQL binding target and trigger, create-object schedule and migration data source, script step paths, BPMN `objectPath` / `workflowPath` / `targetObject`, process-program target, mimic action path, widget workflow / dashboard / mimic / event-prefix paths, and the alarm-bar folder prefix. Typing a path still works. Expressions, driver JSON, and read-only paths stay text. Federation remote path stays text because that object is on the peer, not in the local tree.

### Fixed

- **SQL bindings without a value** — an empty result, SQL `NULL`, a missing column, or a non-numeric value was written as `0` (application bindings: `0` / `""`), indistinguishable from a real zero. The target now keeps its last value marked `quality=BAD` (ADR-0025; trends show a gap), and `ispf.sql_binding.refresh_failures.total{reason}` counts the failure. Numeric text from a text column is parsed instead of becoming `0`. A failed query is counted (`reason=query_failed`).
- **SQL bindings: one failing query no longer stops or undoes the others** — scheduled tree bindings shared one transaction, so a failing query stopped the rest and rolled back values already written by earlier bindings, while the in-memory tree kept showing them. A failing application binding ended its scheduled run and skipped the tree bindings, and an event refresh stopped at the first failing binding. After a function commit, a failing query stopped the remaining bindings and reached the function's caller although the function had committed. Scheduled refreshes now run each binding outside a shared transaction; event refreshes from the event bus already gave each binding its own transaction. In those and in after-commit refreshes a failing query keeps the last value marked `quality=BAD` (`reason=query_failed`), any other failure is logged, and the remaining bindings still run. Deploy and manual refresh still return the error; a refresh inside a workflow step still runs in the step's transaction.
- **SQL bindings: listing no longer rewrites the catalog folder** — every scheduler tick (10 s) and every function call listed the bindings through the catalog ensure, which rewrote `root.platform.bindings` each time (a revision bump, an `UPDATE_INFO` config-audit row, and a config-change event), although the listing methods are marked `readOnly`. The write also locked the folder row until the caller's transaction committed, so concurrent workflow steps and function calls waited on each other. The catalog is ensured at bootstrap and on upsert; listing only reads (ADR-0033).
- **SQL bindings: values refreshed after a function commit are saved** — that refresh ran in the function's finished transaction, so its values reached the live tree but were never written to the database, and a restart brought back the old values; a new transaction there could wait forever on a calling workflow step whose transaction is suspended on the same thread. The commit now hands the refresh to the object-change bus, and a bus worker refreshes each binding outside the function's transaction shortly after the call returns, so the values are saved. One refresh per function runs at a time, and a commit during it makes it run once more, so an older query cannot overwrite a newer value. Like the scheduled refresh, it no longer checks the tenant data-source restriction with the caller's credentials; the restriction is checked when the binding is saved. With `ispf.object-change.async-enabled=false` or a full automation queue, the refresh still runs on the calling thread and reaches only the live tree.
- **Self-diagnostics bootstrap** — a failed ensure of the probe device / dashboard was only a `warn` ("skipped"), so an empty contour looked like a healthy dashboard. Failure now marks the probe `bootstrapStatus=error`, keeps sync off, and rejects enable with `Self-diagnostics is not ready`; `ensure-on-startup=false` marks `skipped`. The server startup chain is not aborted.
- **Periodic bindings after a failure** — a run that threw left `next_run_at` in the past, so the rule fired again on the next wake, and after 5 failures in a row (often within seconds) it was disabled for good: schedule row deleted, `enabled=false` written to `@bindingRules`. A short database outage switched periodic bindings off permanently. A failed run now moves `next_run_at` by one period, doubling per further failure up to `max(periodicMs, 5 min)` (`ispf.binding.periodic.max-backoff-ms`). The rule is never disabled; the first success restores the period. `ispf.binding.periodic.max-consecutive-failures` is removed.
- **Dependabot GHSA stream (Netty / source-map-js)** — transitives still resolved Netty 4.1 below 4.1.137.Final (SNI / codec DoS) and `source-map-js` 1.2.1 in symbol-import / symbol-pack-isa. Root `eachDependency` now lifts Netty 4.1 → **4.1.137.Final** and 4.2 → **4.2.18.Final**; the tools override `source-map-js` **1.2.2**. Moquette GHSA wants **0.18.1**, which is not on Maven Central (latest published is **0.17**, test-only broker); not pinned. Kotlin Gradle Plugin fix is only **2.4.20-Beta1** — not taken.
- **Singleton instance from Web Console** — `GET /api/v1/singleton-blueprints/{id}/instance` created the hub through `BlueprintEngine` only, so the blueprint's binding rules, `sqlBindings`, and `alertRules` were not materialized, unlike the agent's `ensure_singleton_instance`. Both now go through `BlueprintApplicationService.ensureSingletonInstanceWithRules`; a contribution failure is 400 with the message.
- **Alert rules: one failing rule no longer stops the rest, and no double raise** — an exception other than an uncomputable condition or a missing object (a storage error, for example) aborted the remaining rules of the same variable change or poll tick, and two workers could evaluate one rule at once, both raising it or interleaving its sustain and latch timers. Each rule is now evaluated under its own lock and in isolation: a failure is logged and counted in `ispf.alert.rule_failures.total{reason=condition|error}` (System metrics → Alert rule failures), the other rules still run, and the rule is not disabled. A state change made while the runtime flusher was writing the rule is no longer marked clean and lost; the next flush writes it.
- **Script functions saved on the tree** — `PUT /api/v1/objects/by-path/functions` and the agent's `deploy_tree_function` stored any script body, so an unknown step or a missing `return` surfaced only at invoke. Script bodies are now checked on save by the same `FunctionScriptValidator` as bundles (400 / `status: ERROR` with the step message). Expression, object-query, pulse, and Java functions are unchanged.
- **Agent knowledge: MES reference and function host** — the MES playbook, the lite plan, and the MES walkthrough pointed BFF calls at `root.platform.devices.demo-sensor-01`, while the reference functions live on the SINGLETON hub `root.platform.singleton-blueprints.mes-reference-hub-v1`; the walkthrough curl failed. The functions guide deployed a Java `checkThreshold` on a DEVICE, against P6 (threshold → ALERT + CEL). They now name the hub, and the Java example is an on-demand `pumpEfficiency` on a hub.
- **MES reference dispatch dashboard** — List / Start / Complete widgets invoked `mes_*` on `demo-sensor-01`, so the operator buttons missed the functions. Widgets now bind to `mes-reference-hub-v1` (bundle 1.0.2). Alert rules and the event journal stay on the rack device.
- **Workflow cron triggers on a cluster** — the cron poller took a one-shot lock (`tryAcquire`, not renewed in the background) and released it after each tick, so a tick longer than the 60 s TTL let another replica take the lock and start the same due workflows again. The poller now runs under the sticky `LeaderLock.runIfLeader` lease, which is renewed while the tick runs, and stops before the next workflow once `isHeld` turns false (ADR-0028).
- **Driver `telemetryCoalesceMs`** — `DriverBinding.parsePositiveInt` treated an unparsable `telemetryCoalesceMs` as `0`, so a typo silently disabled telemetry coalescing while the operator believed a window was set. A non-numeric value now fails the driver start as a configuration error; an absent, blank, or explicit non-positive value still means coalescing is off.
- **`brace-expansion`** — the web-console npm overrides held 1.1.18 and 2.1.4, which are inside GHSA-6j4f-fj2g-mc7p, GHSA-qhr7-859c-m2p7, and GHSA-q2hr-2g5m-vwhr (stack exhaustion and quadratic brace expansion). The pins are now 1.1.21 and 2.1.7.
- **Driver JSON configuration** — `DriverBinding.parseMap` and `DriverPointMappingParser.parse` treated an unparsable `driverConfigJson` / `driverPointMappingsJson` as an empty map, so a device started with no points and no config instead of a configuration error. Invalid JSON now fails the driver start with the parse message and marks the driver `ERROR`; blank JSON still means no maps.
- **Blueprint catalogs** — Explorer create on instance-types, mixin-blueprints, and singleton-blueprints registers a blueprint of that folder’s kind. An empty blueprint no longer asks for MIXIN, INSTANCE, or SINGLETON. Target object type is asked only for INSTANCE and MIXIN (DEVICE, CUSTOM, DASHBOARD, WORKFLOW, MIMIC, ALERT, REPORT). Export follows the catalog kind; a singleton catalog does not offer export-from-object. `GET /api/v1/blueprints/by-name/{name}` for a missing registry row is 404, and the editor says the tree node was created as an ordinary object.
- **Create object under devices** — the type list is DEVICE, CUSTOM, and a visual group. Instance-type options under devices stay DEVICE.
- **Alert rule conditions** — `AlertRuleService.evaluateCondition` treated an `ExpressionException` as `false`, so a typo looked like an honest unmet threshold and the rule neither raised nor reported failure. Uncomputable `conditionExpr` / `deactivateExpr` now fail that rule evaluation with the expression text (other rules in the same fan-out still run); a computed `false` still means do not raise; orphan missing targets stay soft-disabled.
- **Binding startup** — a startup rule that hit the chain pass limit threw out of `ApplicationReadyEvent` and stopped the whole server. That object is now logged and skipped. Periodic rules already isolated the same failure. On the demostand this was `root.platform.singleton-blueprints.doom`.
- **Admin Copilot collapse** — opening the drawer called `startNewChat()`, so closing it and opening again discarded the thread. The AI button and the header × now only hide and restore the same conversation. **New chat** is the control that clears it.
- **Operator assistant** — close and reopen already kept the thread. The header now has **New chat**, which clears the messages and the session; × still only hides the drawer.
- **Workflow gateway conditions** — `WorkflowConditionFactory.evaluateExpression` treated an `ExpressionException` (and a missing trigger object) as `false`, so a typo looked like an honest false branch and exclusive gateways took the default flow. Uncomputable conditions now fail the step with the expression text; a computed `false` still skips to the next/default flow; a blank condition still means unconditional.
- **Event filter CEL errors** — `EventFilterMatcher` treated an `ExpressionException` as a miss (`false`), so a typo or invalid expression looked like an honest filter rejection and emptied `GET /api/v1/events?filterPath=` / `?expr=`. Uncomputable expressions now fail the filter application with the expression text; a computed `false` still drops the event; a blank expression still means no CEL narrowing.
- **Function invoke `inputSchema`** — invoke preferred a non-empty client schema over the descriptor, so extra fields and the client's types became the function input. Invoke now projects each row onto the descriptor `inputSchema` (drop extras, coerce types, fill a missing value the same way script coercion does). Event, driver-write, and federation payloads still accept a client schema.
- **`INTEGER` fields** — binding rules and script function output coerced any `Number` with `intValue()`, so `1.9` became `1` and values outside `int` range wrapped silently. `DataRecord` only accepted `Integer` and rejected in-range `Long`. Shared `IntegerValues.requireInt` now accepts whole numbers in `int` range (including `Long`) and rejects fractions and out-of-range values on all three paths.
- **Binding expression errors** — `BindingExpressionEvaluator.evaluate` swallowed `ExpressionException` and returned empty, so a typo in a rule expression looked like "Expression returned empty" and left the target unchanged without failing the recalc. Uncomputable expressions now fail with the expression text and engine message; a blank expression still means nothing to evaluate.
- **Binding rules surface failures** — a failed `call()` / `queryScalar()` / `queryRows()` / `fire()`, an uncomputable condition, or a truncated pass/depth chain looked like success or like an honest `false`. Those now fail the recalc with the object path and cause; nested `call()` while one is running is rejected; a computed `false` still skips the rule; a blank condition still means always run.
- **Object-query honesty** — blank or unparsable aggregate cells were treated as `0`, and a historian column failure became a blank cell so the query looked empty of samples. Blanks are skipped (a real `0` still counts); non-numbers and historian errors fail the query (member ACL denial still omits the column); `min` / `max` / `avg` over no numeric cells fail instead of returning `0`. `queryScalar` / `queryRows` no longer treat a single-quoted JSON literal as a variable path when the text contains `/` (common in `"path":"…"`); quotes are stripped before the ref-vs-JSON decision.
- **Java functions** — `bootRun` now reads the single classpath jar's manifest `Class-Path` for `javac` (fixes `classpath missing ispf-core`); invoke projects the result onto `descriptor.outputSchema()` like script functions (drop extras, fail on missing required fields, coerce numbers). `FunctionScriptValidator` accepts Object Query step types the engine already runs (`queryRows`, `scan_objects`, `for_each_row`, `apply_query_patch`) instead of failing saves with "Unknown script step type".
- **DEVICE delete stops drivers** — HTTP / UI / bulk delete removed the tree node but left in-memory polls running; orphan `POST .../drivers/runtime/stop` then 404'd while writing `driverStatus`. Delete stops every active driver under the path (including children) first; orphan stop / queued polls no longer require the tree node.
- **BPMN due timers and work queue** — boundary and intermediate catch deadlines were registered but never fired on their own (only `POST .../instances/{id}/timer`). A leader-locked `WorkflowDueTimerScheduler` polls waiting instances (`ispf.workflow.timer-poll-ms`, default 2s); the manual endpoint remains for tests and forced fire. After a `cancelActivity=true` boundary timer, saving the instance completes orphan `OPEN`/`CLAIMED` work-queue rows whose node is no longer pending. A failed `ispf:function` on user-task complete now rejects with `WorkflowException` instead of logging and still closing the task.
- **`DOUBLE` fields** — `DataRecord` now stores any finite number as `double` (JSON integers larger than 32 bits no longer fail as "must be double").
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
