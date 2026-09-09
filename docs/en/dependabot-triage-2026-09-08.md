# Dependabot triage (2026-09-08)

Snapshot + landing notes for Dependabot PRs opened while OT Pilot evidence was in flight.

## Landed (2026-09-08)

All Batch A / Batch B patches and the carefully reviewed majors below were squash-merged (or remade + merged) into `main`:

- GHA / npm / Gradle patch-minors from the morning queue (`#165`–`#176`, `#178`–`#181`, remakes as needed)
- Flake that blocked parquet sibling CI was fixed in [`#182`](https://github.com/iot-solutions-ru/ispf/pull/182) (`VariableHistoryAsyncWriterTest` asserts flush **totals**)

Grouping in `.github/dependabot.yml` (parquet / protobuf / npm minor-patch / GHA minor-patch) applies to **future** Dependabot runs.

## Landed later

| PR | Update | Notes |
|----|--------|-------|
| vitest **4→5** | Intentional remake: local `vitest run` **524/524**; Node 22 + Vite 8 meet Vitest 5 floor. Dependabot [#177](https://github.com/iot-solutions-ru/ispf/pull/177) closed after merge. |

## Noise reduction (already on main)

`.github/dependabot.yml` groups:

- Gradle: `parquet*`, `protobuf*`, and remaining minor/patch
- npm: prod + dev minor/patch (majors stay solo)
- GitHub Actions: minor/patch only (majors stay solo)
