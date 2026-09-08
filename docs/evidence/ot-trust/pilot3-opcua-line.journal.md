# Pilot #3 soak journal — OPC UA line

Phase 25 / BL-140.  
Checklist: [pilot3-opcua-line.checklist.md](pilot3-opcua-line.checklist.md).  
Day-1: [2026-09-08-bl140-pilot3-opcua-day1.md](2026-09-08-bl140-pilot3-opcua-day1.md).

> **Honesty:** Same-host `opcua-server`↔`opcua` loopback on lab ISPF. Not external PLC. Not OT 10/10.

## Header

| Field | Value |
|-------|-------|
| Pilot id | `pilot3-opcua-line` |
| Site / VLAN | `lab-ot-vlan-192.168.100` |
| Protocol | `opcua` + `opcua-server` |
| ISPF | `0.9.207` (`ispf-enterprise-l`) |
| Start (UTC) | `2026-09-08` |
| Target end | `2026-09-15` |

## Validation §3

| Check | Result | Evidence |
|-------|--------|----------|
| Browse | ☑ | day1 |
| Read ≥10 tags | ☑ | 12 |
| Write-back | ☑ | day1 |
| Historian | ☑ | lineSpeed/cellTemp |
| C5 recover | ☑ | day1 |

## Daily log

| Day | Date (UTC) | Tags online | Incidents | Historian OK | Write OK | Notes |
| --- | ---------- | ----------- | --------- | ------------ | -------- | ----- |
| 1 | 2026-09-08 | 12 | none | ☑ | ☑ | Loopback UA; packs resignatured — [day1](2026-09-08-bl140-pilot3-opcua-day1.md) |
| 2 | | | | ☐ | ☐ | |
| 3 | | | | ☐ | ☐ | |
| 4 | | | | ☐ | ☐ | |
| 5 | | | | ☐ | ☐ | |
| 6 | | | | ☐ | ☐ | |
| 7 | | | | ☐ | ☐ | Sign-off? |
