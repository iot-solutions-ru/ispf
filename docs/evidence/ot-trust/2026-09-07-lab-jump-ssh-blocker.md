# Lab jump SSH — key expected, private key missing on this agent VM

Date: 2026-09-07

## Canonical auth (repo)

| Item | Value |
|------|-------|
| Jump | `84.42.21.226:5031` user `iot-solutions` |
| Identity (docs / `.env.example`) | `~/.ssh/lab_ed25519` (`ISPF_LAB_SSH_IDENTITY_FILE`) |
| One-time install | `python deploy/local/tools/lab-ssh-install-key.py` with `ISPF_LAB_PASSWORD` |

## What this agent found

| Check | Result |
|-------|--------|
| `~/.ssh/lab_ed25519` on boot | **Absent** (only `known_hosts*` from prior sessions) |
| Password via `SSHPASS` | **Rejected** |
| Key auth | **Denied** — no matching private key on disk / agent |

## Action taken on this VM (ephemeral)

Regenerated a **new** local key for this cloud agent (not the historical workstation key):

| Field | Value |
|-------|-------|
| Path | `~/.ssh/lab_ed25519` (gitignored / not committed) |
| Pubkey | `ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJ65KeCzo1dvWseVriOAkMD3+OaBArhHOMiomF/qDXKc cursor-cloud-agent-lab@ispf` |
| Fingerprint | `SHA256:AGURk2soPxlNQ3zdFu9bGU1CpyN34KAfv68kZbynezg` |
| Installed on jump? | **No** — password install failed |

## What ops / user need to do (pick one)

1. **Restore the old private key** into this agent (Cursor secret → `~/.ssh/lab_ed25519`) if the historical pubkey is already in jump `authorized_keys`.
2. **Or** append the **new** pubkey above to `iot-solutions@84.42.21.226` `~/.ssh/authorized_keys` (and optionally to `192.168.100.10`).
3. **Or** temporarily set a working `ISPF_LAB_PASSWORD` / `SSHPASS` so the agent can run `ssh-copy-id` once.

Until then: Pilot #1 lab timer may still run on-box; agent cannot pull soak / bootstrap Pilot #2 MQTT from jump.
