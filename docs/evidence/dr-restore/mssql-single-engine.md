# MSSQL single-engine restore sketch

> **Status:** Procedure only. **No drill has been run.** Do not publish RPO or RTO from this page.  
> Contract: [ADR-0068](../../en/decisions/0068-backup-and-dr.md) rule 4.

## What one database backup covers

Metadata, event journal, and JDBC variable history **if** they live in that database.

## What it does not cover

- Secrets-key material (T4) — restore the key from escrow or sign-in secrets stay unreadable.
- Local JDBC buffer files (`ISPF_MSSQL_JDBC_BUFFER`) — they can be ahead of or behind the backup. Do not replay them into a restored database until Gate C has a unique key; otherwise rows duplicate.
- A second ISPF node. Active-active on MSSQL is refused.

## Drill checklist (when someone runs it)

Record the date, edition, and backup type (full + log). After restore, check:

1. Login.
2. One device poll.
3. One history query.
4. One journal row.

File the transcript next to this note before any RPO/RTO sentence.
