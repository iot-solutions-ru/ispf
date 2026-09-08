# BL-140 Pilot #3 — OPC UA line checklist (C1–C6)

> **Status:** Lab day 1 green (2026-09-08) on same-host UA loopback.  
> Playbook: [field-pilot-playbook §3](../../en/field-pilot-playbook.md#3--opc-ua-line-pilot).  
> Journal: [pilot3-opcua-line.journal.md](pilot3-opcua-line.journal.md).  
> Day-1: [2026-09-08-bl140-pilot3-opcua-day1.md](2026-09-08-bl140-pilot3-opcua-day1.md).

## Honesty gate

| Claim | Allowed when |
|-------|----------------|
| Lab loopback green | Server+client RUNNING, browse+R/W |
| External PLC peer | Named endpoint / opc-plc fixture (open — DNS blocked mcr pull) |
| BL-140 OPC UA pilot Done | 7-day soak + OT sign-off |

---

## C1 — Site intake

| Field | Value |
|-------|-------|
| Pilot id | `pilot3-opcua-line` |
| Site | `lab-ot-vlan-192.168.100` |
| Site type | ☑ Internal OT lab VLAN (loopback UA) ☐ External PLC ☐ Customer plant |
| Endpoint | `opc.tcp://127.0.0.1:4840/ispf` (in-container) |
| Start (UTC) | `2026-09-08` |
| End T+7 | `2026-09-15` |

**C1 Done** ✅ (lab loopback named)

---

## C2 — Device config

| Setting | Applied |
|---------|---------|
| Server `opcua-server` bindPort 4840 / endpointPath `/ispf` | ☑ |
| Client `opcua` endpointUrl `opc.tcp://127.0.0.1:4840/ispf` | ☑ |
| ≥10 tags (`lineSpeed`, `cellTemp`, `tag00`…`tag09`) | ☑ |
| Historian on lineSpeed + cellTemp | ☑ |

---

## C3 — Validation §3

| Check | Result |
|-------|--------|
| Browse | ☑ |
| Subscribe / poll | ☑ |
| Write-back | ☑ |
| Partner read (external UA Expert) | ☐ deferred (no external client this session) |
| Mimic | ☐ deferred |

---

## C4 / C5 / C6

Soak journal days 1–7; C5 server-stop exercise logged in day-1 JSON; sign-off after day 7.
