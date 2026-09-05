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

- **Marketplace `mqtt-temperature` migrations** — aligned with `examples/lab-mqtt-temperature`
  H2-compatible SQL (`DOUBLE` / `TIMESTAMP` / PK syntax).

### Added

- **Enterprise L catalog tooling** (tracked under `tools/historian-scale/`): seed 50k
  history-enabled devices, `GET /api/v1/platform/analytics/history-enabled-count`, and
  analytics-scale gate counting **history-enabled variables** (not binding-rule `/tags`).
- **HMI offline lab soak** — `npm run pwa:offline-field-soak` (Playwright CDP offline, configurable
  duration; demostand **2 h** evidence archived for Post-S33).

### Added

- **OT Trust Wave 2 codec promotion** — `redis`, `mitsubishi-slmp`, `yaskawa-memobus`, `sparkplug-b`
  promoted STUB → **PRODUCTION** with real codecs + in-process loopback tests (fake RESP / SLMP 3E /
  Modbus TCP / Moquette+Sparkplug protobuf); matrix `POLL_WRITE`; evidence
  [`docs/evidence/ot-trust/2026-09-05-wave2-codec-promotion.md`](docs/evidence/ot-trust/2026-09-05-wave2-codec-promotion.md).

### Docs

- **OT Trust — raise readiness for all 162 packs** — stub-kit **v0.2** (lab loopback write) +
  contract tests for catalog stubs → audit label **`STUB_LAB`** (honest: still no protocol codec);
  `tools/driver-stubs/raise-stub-readiness.py`; WRITE_UNDERCLAIM false-positives for ingress read-only drivers fixed.
- **OT Trust — full 162-driver readiness audit** — `tools/driver-readiness-audit.py` + report
  [`docs/evidence/ot-trust/driver-readiness.md`](docs/evidence/ot-trust/driver-readiness.md)
  (matrix honesty: promote `email`/`sms`/`webhook`/`smb`, claim `smpp` WRITE; CI gate on interop workflow).
- **OT Trust Wave 1 hardening** — writable Modbus lab fixture (`deploy/driver-interop/modbus/server.py`) replaces `oitc/modbus-server` (FC6 reset in CI); smoke covers FC6+FC16 + optional OPC UA write; writePoint↔capability source gate; lab day-1 journal.
- **OT Trust Wave 1 kickoff** — unpark P-OT; [ADR-0057](docs/en/decisions/0057-ot-trust-wave1-dnp3-poll-only.md) (DNP3 PRODUCTION poll-only); `http` WRITE in production matrix; evidence `docs/evidence/ot-trust/`.
- **G-01 pen-test prep** — [pen-test-scope.md](docs/en/pen-test-scope.md) SOW / rules of engagement; evidence folder stub `docs/evidence/security-pentest/`.
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
