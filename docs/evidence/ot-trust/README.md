# OT Trust evidence (Phase 25)

> Honesty: this folder tracks **process evidence**, not a completed field certification.  
> Lab/matrix readiness ≠ competitive OT **10/10** and ≠ BL-140 plant Done.

| Status | Note |
|--------|------|
| **Catalog closed (lab)** | Waves **1–11** merged — stub list **0**, audit **162 / 162**, FAIL/WARN **0** ([#144](https://github.com/iot-solutions-ru/ispf/pull/144)) |
| **Field trust** | **In progress** — Pilot #1 days **1–3**; Pilot #2 MQTT day **1**; Pilot #3 OPC UA loopback day **1** on `lab-ot-vlan-192.168.100`. Not OT 10/10. |
| Decision | [ADR-0057](../../en/decisions/0057-ot-trust-wave1-dnp3-poll-only.md) — DNP3 PRODUCTION poll-only |
| Post-merge honesty | [2026-09-06-post-merge-lab-vs-field.md](2026-09-06-post-merge-lab-vs-field.md) — lab ≠ field; TOP-20 fixture gaps |
| BL-141 fixture depth | [2026-09-06-bl141-snmp-http-fixtures.md](2026-09-06-bl141-snmp-http-fixtures.md) — +SNMP/+HTTP; [2026-09-06-bl141-bacnet-fixture.md](2026-09-06-bl141-bacnet-fixture.md) — +BACnet; [2026-09-06-bl141-iec104-fixture.md](2026-09-06-bl141-iec104-fixture.md) — +IEC104; [2026-09-06-bl141-ethernet-ip-fixture.md](2026-09-06-bl141-ethernet-ip-fixture.md) — +EtherNet/IP; [2026-09-06-bl141-dlms-fixture.md](2026-09-06-bl141-dlms-fixture.md) — +DLMS; [2026-09-06-bl141-modbus-udp-fixture.md](2026-09-06-bl141-modbus-udp-fixture.md) — +Modbus UDP; [2026-09-06-bl141-dnp3-fixture.md](2026-09-06-bl141-dnp3-fixture.md) — +DNP3; [2026-09-06-bl141-s7-fixture.md](2026-09-06-bl141-s7-fixture.md) — +S7 SoftPlc; [2026-09-06-bl141-modbus-rtu-fixture.md](2026-09-06-bl141-modbus-rtu-fixture.md) — +Modbus RTU; [2026-09-06-bl141-gps-tracker-fixture.md](2026-09-06-bl141-gps-tracker-fixture.md) — +GPS tracker (**14/20**) |
| Kickoff | [2026-09-05-wave1-kickoff.md](2026-09-05-wave1-kickoff.md) |
| Lab day-1 dry-run | [2026-09-05-lab-modbus-day1.md](2026-09-05-lab-modbus-day1.md) |
| **Full catalog (162)** | [driver-readiness.md](driver-readiness.md) · [driver-readiness.json](driver-readiness.json) |
| Wave promotions | `2026-09-05-wave2…10-*.md`, [2026-09-06-wave11-codec-promotion.md](2026-09-06-wave11-codec-promotion.md) |
| Stub readiness ladder (historical) | [2026-09-05-raise-all-driver-readiness.md](2026-09-05-raise-all-driver-readiness.md) — then Waves cleared stubs → PRODUCTION labs |
| Pilot journal template | [pilot-soak-journal.template.md](pilot-soak-journal.template.md) |
| **BL-140 Pilot #1 kickoff** | [2026-09-06-bl140-pilot1-kickoff.md](2026-09-06-bl140-pilot1-kickoff.md) · [checklist](pilot1-modbus-plant.checklist.md) · [journal](pilot1-modbus-plant.journal.md) |
| **BL-140 Pilot #1 lab day 1** | [2026-09-06-bl140-pilot1-lab-day1.md](2026-09-06-bl140-pilot1-lab-day1.md) · [JSON](pilot1-lab/day1-2026-09-06.json) |
| **BL-140 Pilot #1 C5 disconnect** | [2026-09-06-bl140-pilot1-c5-disconnect.md](2026-09-06-bl140-pilot1-c5-disconnect.md) · [JSON](pilot1-lab/c5-2026-09-06.json) |
| **BL-140 Pilot #1 day 2** | [2026-09-07-bl140-pilot1-day2.md](2026-09-07-bl140-pilot1-day2.md) · [JSON](pilot1-lab/soak-day2-2026-09-07.json) |
| **BL-140 Pilot #1 day 3** | [2026-09-08-bl140-pilot1-day3.md](2026-09-08-bl140-pilot1-day3.md) · [JSON](pilot1-lab/soak-day3-2026-09-08.json) |
| **BL-140 Pilot #2 MQTT kickoff** | [2026-09-07-bl140-pilot2-mqtt-kickoff.md](2026-09-07-bl140-pilot2-mqtt-kickoff.md) · [checklist](pilot2-mqtt-fleet.checklist.md) · [journal](pilot2-mqtt-fleet.journal.md) |
| **BL-140 Pilot #2 MQTT day 1** | [2026-09-08-bl140-pilot2-mqtt-day1.md](2026-09-08-bl140-pilot2-mqtt-day1.md) · [JSON](pilot2-lab/day1-2026-09-08.json) · [C5](pilot2-lab/c5-2026-09-08.json) |
| **BL-140 Pilot #3 OPC UA day 1** | [2026-09-08-bl140-pilot3-opcua-day1.md](2026-09-08-bl140-pilot3-opcua-day1.md) · [checklist](pilot3-opcua-line.checklist.md) · [journal](pilot3-opcua-line.journal.md) · [JSON](pilot3-lab/day1-2026-09-08.json) |
| Parallel timers | [2026-09-08-bl140-parallel-timers.md](2026-09-08-bl140-parallel-timers.md) — 06:00 / 06:05 / 06:10 MSK |
| Lab jump SSH | [2026-09-07-lab-jump-ssh-blocker.md](2026-09-07-lab-jump-ssh-blocker.md) — **resolved** 2026-09-08 (key auth) |
| Daily soak check | [`tools/ot-trust/pilot1-modbus-soak-check.py`](../../tools/ot-trust/pilot1-modbus-soak-check.py) (lab timer 06:00 MSK) |
| MQTT soak / bootstrap | [`pilot2-mqtt-soak-check.py`](../../tools/ot-trust/pilot2-mqtt-soak-check.py) · [`pilot2-mqtt-lab-bootstrap.py`](../../tools/ot-trust/pilot2-mqtt-lab-bootstrap.py) |
| Pull soak evidence via jump | [`pull-pilot-soak-evidence.sh`](../../tools/ot-trust/pull-pilot-soak-evidence.sh) |

## Unpark / progress

Operator named task started **OT Trust Wave 1** (2026-09-05). Catalog codec promotions through Wave 11 closed the **stub width** track. BL-141 TOP-20 compose peers reached **14/20** (intentional gaps remain).

**Field track:** Pilot #1 Modbus days 1–3; Pilot #2 MQTT day 1; Pilot #3 OPC UA same-host loopback day 1. Daily timers 06:00/06:05/06:10 MSK. Jump key auth OK.

Do **not** claim competitive-scorecard OT **10/10** until BL-140 field pilots complete soak + sign-off.
