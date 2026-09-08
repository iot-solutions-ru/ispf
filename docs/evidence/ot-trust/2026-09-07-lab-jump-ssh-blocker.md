# Lab jump SSH — access restored (2026-09-08)

## Canonical auth

| Item | Value |
|------|-------|
| Jump | `84.42.21.226:5031` user `iot-solutions` |
| Identity | `~/.ssh/lab_ed25519` (`ISPF_LAB_SSH_IDENTITY_FILE`) |
| Lab | `192.168.100.10` (passwordless from jump) |

## Resolution

| Step | Result |
|------|--------|
| Password login (ops-provided, not committed) | OK on jump |
| Install agent pubkey into jump `authorized_keys` | OK |
| Key auth `lab-jump` | OK |
| Jump → `192.168.100.10` | OK (`spark-1f58`) |

Agent pubkey fingerprint: `SHA256:AGURk2soPxlNQ3zdFu9bGU1CpyN34KAfv68kZbynezg`

## Follow-on unlocked

- Pilot #1 soak day 3 pulled
- Pilot #2 MQTT Mosquitto + device day 1 completed

Do **not** commit passwords. Prefer key auth going forward.
