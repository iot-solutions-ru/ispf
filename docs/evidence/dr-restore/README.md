# DR restore drill evidence (ADR-0068)

> ADR: [0068-backup-and-dr](../../en/decisions/0068-backup-and-dr.md) · Script: [`deploy/tools/dr-restore-drill.sh`](../../../deploy/tools/dr-restore-drill.sh)

## Claim boundary

| Claim | Today |
|-------|--------|
| T2 lab dump→restore script (seeded Postgres, row-count check) | **Lab Done** — `deploy/tools/dr-restore-drill.sh` |
| Optional login smoke when `ISPF_BASE_URL` points at a running server | **Partial** — opt-in in the same script |
| T1 platform backup import smoke (Java) | **Partial** — existing `PlatformBackup*` tests; not this script |
| Dated CI/lab transcript archived here | Add `docs/evidence/dr-restore/<date>-….md` after a green run |
| Customer / site DR, RPO/RTO, T3 historian, T4 secrets-key escrow | **Gap** — do not claim |

## How to run

```bash
bash deploy/tools/dr-restore-drill.sh
# → build/dr-restore-drill/transcript.md
```

Copy a redacted transcript into this folder when refreshing the board. Scorecard Deploy must **not** jump to 9+ on lab dump alone.
