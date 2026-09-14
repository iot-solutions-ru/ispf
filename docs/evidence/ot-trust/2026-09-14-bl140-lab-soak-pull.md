# BL-140 OT Trust lab soak pull — 2026-09-10…14

Date: 2026-09-14  
Site: `lab-ot-vlan-192.168.100`  
Access: jump `84.42.21.226:5031` → lab `192.168.100.10` with local `~/.ssh/ispf_lab_ed25519`  
(`ISPF_LAB_SSH_IDENTITY_FILE`). Supersedes the 2026-09-12 Cloud Agent key blocker ([#212](https://github.com/iot-solutions-ru/ispf/pull/212)).

## Result (daily timer JSON)

| Pilot | Latest UTC | Status | Pass | Evidence |
|-------|------------|--------|------|----------|
| P1 Modbus | 2026-09-14T03:00:04Z | RUNNING / 50 tags | **true** | [`pilot1-lab/soak-2026-09-14.json`](pilot1-lab/soak-2026-09-14.json) |
| P2 MQTT | 2026-09-14T03:05:04Z | RUNNING / 10 tags | **true** | [`pilot2-lab/soak-2026-09-14.json`](pilot2-lab/soak-2026-09-14.json) |
| P3 OPC UA | 2026-09-14T03:10:05Z | RUNNING / 12 tags | **true** | [`pilot3-lab/soak-2026-09-14.json`](pilot3-lab/soak-2026-09-14.json) |

Also pulled continuous daily files `soak-2026-09-10.json` … `soak-2026-09-13.json` for each pilot under `pilot{1,2,3}-lab/`.

## Honesty

- Lab timers only — **not** field Done / **not** OT 10/10.
- 7-day soak window for P1 now has continuous JSON through day **14** calendar (from day1 2026-09-06).
- Does not replace a named plant soak journal.

## Unblock note

Cloud Agent still needs secret `ISPF_LAB_SSH_PRIVATE_KEY` for unattended pulls. Operator workstation with `ispf_lab_ed25519` can pull evidence now.

**Follow-up:** soak **stopped** 2026-09-14 — [2026-09-14-bl140-lab-soak-stopped.md](2026-09-14-bl140-lab-soak-stopped.md).
