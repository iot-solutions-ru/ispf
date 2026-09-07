# Lab access blocker — jump SSH (2026-09-07)

| Field | Value |
|-------|-------|
| Jump | `84.42.21.226:5031` user `iot-solutions` |
| Lab | `192.168.100.10` (passwordless from jump when jump auth works) |
| Symptom | `Permission denied` on password auth from cloud agent (`SSHPASS` env present, length 17) |
| Impact | Cannot pull Pilot #1 day 3+ evidence live; cannot bootstrap Pilot #2 MQTT on lab Mosquitto from this agent |
| Not affected | Pilot #1 daily timer on lab (if still armed); demostand HTTPS API; repo docs/tools |

## Mitigation

1. Ops: rotate / re-inject jump password into agent secrets (do **not** commit).
2. Until then: keep parallel tracks (HMI-8H close, Pilot #2 kickoff pack, soak pull helper).
3. Re-run: `./tools/ot-trust/pull-pilot-soak-evidence.sh` after credentials work.

## Honesty

This note is an **access** residual, not an OT protocol failure. Do not mark Pilot #1 soak failed solely because the agent cannot SSH today.
