# Dependabot triage (2026-09-08)

Snapshot of open Dependabot PRs while Pilot evidence PR [#164](https://github.com/iot-solutions-ru/ispf/pull/164) is in flight. Agent `gh` cannot merge; land these in the GitHub UI (or squash green patch sets manually).

## Recommended merge order

### Batch A — safe green (merge anytime, prefer grouped)

| PR | Update | Notes |
|----|--------|--------|
| [#173](https://github.com/iot-solutions-ru/ispf/pull/173) | `actions/deploy-pages` 5.0.0→5.0.1 | GHA patch |
| [#169](https://github.com/iot-solutions-ru/ispf/pull/169) | `@types/react-dom` 19.2.5→19.2.7 | types |
| [#172](https://github.com/iot-solutions-ru/ispf/pull/172) | `@ant-design/icons` 6.3.2→6.3.4 | patch |
| [#174](https://github.com/iot-solutions-ru/ispf/pull/174) | `framer-motion` 13.1.1→13.2.0 | minor |
| [#175](https://github.com/iot-solutions-ru/ispf/pull/175) | `react-i18next` 17.0.12→17.0.13 | patch |
| [#176](https://github.com/iot-solutions-ru/ispf/pull/176) | `@testing-library/user-event` 14.6.6→14.6.7 | patch |
| [#178](https://github.com/iot-solutions-ru/ispf/pull/178) | `i18next` 26.4.0→26.4.2 | patch |
| [#179](https://github.com/iot-solutions-ru/ispf/pull/179) | `@playwright/test` 1.62.1→1.63.0 | minor |
| [#180](https://github.com/iot-solutions-ru/ispf/pull/180) | `react-map-gl` 8.1.2→8.1.3 | patch |

### Batch B — keep siblings together

| PRs | Update | Notes |
|-----|--------|--------|
| [#168](https://github.com/iot-solutions-ru/ispf/pull/168) + [#170](https://github.com/iot-solutions-ru/ispf/pull/170) + [#171](https://github.com/iot-solutions-ru/ispf/pull/171) | `protobuf-java` / `javalite` / `java-util` 4.36.0→4.36.1 | Merge as a set (or land one then rebase siblings) |
| [#166](https://github.com/iot-solutions-ru/ispf/pull/166) + [#167](https://github.com/iot-solutions-ru/ispf/pull/167) | `parquet-avro` + `parquet-hadoop` 1.18.0→1.18.1 | See flake note below |

### Hold / review carefully

| PR | Update | Why |
|----|--------|-----|
| [#165](https://github.com/iot-solutions-ru/ispf/pull/165) | `actions/upload-artifact` **4→7** | Major GHA; CI green but verify artifact consumers / retention |
| [#177](https://github.com/iot-solutions-ru/ispf/pull/177) | `vitest` **4→5** | Major test-runner; CI green but treat as deliberate upgrade |

## #167 failure (likely flaky, not parquet)

- CI: `backend` failed after ~46m — **1** of 1148 tests.
- Failed test: `VariableHistoryAsyncWriterTest.flushesBatchWhenBatchSizeReached` — Mockito `ArgumentsAreDifferent` on `recordVariableHistoryFlushed(2)` (async flush timing).
- Sibling [#166](https://github.com/iot-solutions-ru/ispf/pull/166) (`parquet-avro` only) passed the same backend gate.
- Action: **re-run** failed jobs on #167, or bump both parquet artifacts in one commit, then close the orphan PR.

## Noise reduction

`.github/dependabot.yml` now groups:

- Gradle: `parquet*`, `protobuf*`, and remaining minor/patch
- npm: prod + dev minor/patch (majors stay solo)
- GitHub Actions: minor/patch only (majors stay solo)

Existing open PRs are not auto-collapsed; grouping applies to **future** Dependabot runs.
