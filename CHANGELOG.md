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

- Closed BL-154 per-variable ACL trusted-channel gaps: `MEMBER` enforcement now
  covers analytics query/export/expression, federation tunnel on-behalf-of
  requests, agent history and analytics tools, WebSocket delivery, the object
  editor, expression evaluation, and Haystack/Brick semantic export/query.
- Closed the G-05 HTTP federation peer on-behalf-of residual: the hub forwards
  user, comma-separated roles, and tenant headers, while the peer limits delegated
  roles to the channel ∩ on-behalf-of intersection before `MEMBER` enforcement.
  Health probes remain channel-only.

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

[Unreleased]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.189...HEAD
[0.9.189]: https://github.com/iot-solutions-ru/ispf/compare/v0.9.188...v0.9.189
[0.9.188]: https://github.com/iot-solutions-ru/ispf/releases/tag/v0.9.188
