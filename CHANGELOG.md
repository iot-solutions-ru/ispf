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

### Documentation

- Post-S33 **scorecard / tender honesty**: demostand pin **0.9.192**; AI soft re-soak residual closed at **0.9.191**; G-03 / multi-tenant wording matches PostgreSQL RLS Done; ADR-0055 formal CEL in expression-language (+ demostand verify smoke evidence).

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

[Unreleased]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.192...HEAD
[0.9.192]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.191...v0.9.192
[0.9.191]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.190...v0.9.191
[0.9.190]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.189...v0.9.190
[0.9.189]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.188...v0.9.189
[0.9.188]: https://github.com/iot-solutions-ru/ispf/releases/tag/v0.9.188
