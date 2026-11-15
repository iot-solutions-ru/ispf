# DR restore drill — CI lab T2 (2026-11-14)

> Source: GitHub Actions run [`43648297503`](https://github.com/iot-solutions-ru/ispf/actions/runs/43648297503) on `feat/wave1-ops-harden` (merged as [#733](https://github.com/iot-solutions-ru/ispf/pull/733)).  
> Script: `deploy/tools/dr-restore-drill.sh`

## Claim boundary

| Claim | This run |
|-------|----------|
| T2 Postgres dump→restore row-count check | **PASS** (lab) |
| Optional ISPF login | **skipped** (`ISPF_BASE_URL` unset) |
| T1 tree import / T3 historian / T4 secrets-key | **Not restored** |
| Site DR / RPO-RTO | **Not claimed** |

## Transcript (redacted)

```text
# DR restore drill transcript

- date: 2026-11-14T22:29:23Z
- image: timescale/timescaledb:latest-pg16
- tiers restored: **T2** (Postgres dump→restore lab)
- tiers NOT restored: T1 tree import, T3 historian, T4 secrets-key
- flyway_schema_history: src=1 dst=1
- users: src=1 dst=1
- optional login: skipped
- result: PASS
```

Scorecard Deploy must **not** jump to 9+ on this lab dump alone.
