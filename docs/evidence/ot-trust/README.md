# OT Trust evidence (Phase 25)

> Honesty: this folder tracks **process evidence**, not a completed field certification.  
> Lab/matrix readiness ≠ competitive OT **10/10** and ≠ BL-140 plant Done.

| Status | Note |
|--------|------|
| **Catalog closed (lab)** | Waves **1–11** merged — stub list **0**, audit **162 / 162**, FAIL/WARN **0** ([#144](https://github.com/iot-solutions-ru/ispf/pull/144)) |
| **Field trust** | Still open — needs named plant + soak journals |
| Decision | [ADR-0057](../../en/decisions/0057-ot-trust-wave1-dnp3-poll-only.md) — DNP3 PRODUCTION poll-only |
| Post-merge honesty | [2026-09-06-post-merge-lab-vs-field.md](2026-09-06-post-merge-lab-vs-field.md) — lab ≠ field; TOP-20 fixture gaps |
| BL-141 fixture depth | [2026-09-06-bl141-snmp-http-fixtures.md](2026-09-06-bl141-snmp-http-fixtures.md) — +SNMP/+HTTP; [2026-09-06-bl141-bacnet-fixture.md](2026-09-06-bl141-bacnet-fixture.md) — +BACnet; [2026-09-06-bl141-iec104-fixture.md](2026-09-06-bl141-iec104-fixture.md) — +IEC104; [2026-09-06-bl141-ethernet-ip-fixture.md](2026-09-06-bl141-ethernet-ip-fixture.md) — +EtherNet/IP; [2026-09-06-bl141-dlms-fixture.md](2026-09-06-bl141-dlms-fixture.md) — +DLMS; [2026-09-06-bl141-modbus-udp-fixture.md](2026-09-06-bl141-modbus-udp-fixture.md) — +Modbus UDP; [2026-09-06-bl141-dnp3-fixture.md](2026-09-06-bl141-dnp3-fixture.md) — +DNP3; [2026-09-06-bl141-s7-fixture.md](2026-09-06-bl141-s7-fixture.md) — +S7 SoftPlc (**12/20**) |
| Kickoff | [2026-09-05-wave1-kickoff.md](2026-09-05-wave1-kickoff.md) |
| Lab day-1 dry-run | [2026-09-05-lab-modbus-day1.md](2026-09-05-lab-modbus-day1.md) |
| **Full catalog (162)** | [driver-readiness.md](driver-readiness.md) · [driver-readiness.json](driver-readiness.json) |
| Wave promotions | `2026-09-05-wave2…10-*.md`, [2026-09-06-wave11-codec-promotion.md](2026-09-06-wave11-codec-promotion.md) |
| Stub readiness ladder (historical) | [2026-09-05-raise-all-driver-readiness.md](2026-09-05-raise-all-driver-readiness.md) — then Waves cleared stubs → PRODUCTION labs |
| Pilot journal template | [pilot-soak-journal.template.md](pilot-soak-journal.template.md) |

## Unpark / progress

Operator named task started **OT Trust Wave 1** (2026-09-05). Catalog codec promotions through Wave 11 closed the **stub width** track.

Customer plant still optional; docker fixtures (`deploy/driver-interop`) count for BL-141 lab depth only. Prefer deepening TOP-20 compose/smoke coverage over new protocol packs.

Do **not** claim competitive-scorecard OT **10/10** until BL-140 field pilots + soak journals exist.
