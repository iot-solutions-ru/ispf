# OT Trust Wave 1 kickoff — 2026-09-05

| Field | Value |
|-------|-------|
| Named task | Start Phase 25 OT Trust Wave 1 |
| Lead track | Matrix honesty residual (A1) + Modbus lab interop (B/C dry-run) |
| ADR | [0057](../../en/decisions/0057-ot-trust-wave1-dnp3-poll-only.md) |
| Plant | **Lab only** (`deploy/driver-interop` fixtures) until a customer site is named |

## Snapshot (code truth)

| Driver | Matrix maturity | Caps | Note |
|--------|-----------------|------|------|
| `opc-da`, `opc-bridge` | BETA | POLL | Shells — BL-191 Done |
| `dnp3` | PRODUCTION | POLL | Write throws — A1 = keep poll-only PRODUCTION |
| `ethernet-ip` | PRODUCTION | POLL+WRITE+QUALITY | Real CIP write |
| `http` | PRODUCTION | POLL+WRITE (after kickoff fix) | Code had write; matrix under-claimed |
| `snmp` | PRODUCTION | POLL+WRITE | SET path real |

## Next checklist

- [x] Unpark **P-OT** → In progress  
- [x] A1 decision recorded (ADR-0057)  
- [x] Align `http` WRITE in matrix  
- [ ] B1 smoke: Modbus FC6 write round-trip against docker fixture  
- [ ] B2 upload interop summary artifact in CI  
- [ ] C1–C3 lab Modbus dry-run journal (day 1)  
- [ ] Customer site named (optional for lab track)

## Honesty

Kickoff ≠ field Done. Scorecard OT connectivity stays **PARTIAL** until pilots + journals.
