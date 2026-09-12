# OT Trust — soak evidence pull blocked (2026-09-12)

## Intent

Pull Pilot #1 / #2 / #3 daily soak JSON for **2026-09-10 … 2026-09-12** and update journals after the last committed day notes (**2026-09-09**).

## Result

| Step | Result |
|------|--------|
| `tools/ot-trust/pull-pilot-soak-evidence.sh` | **FAIL** |
| Identity `~/.ssh/lab_ed25519` (`ISPF_LAB_SSH_IDENTITY_FILE`) | **Missing** in this Cloud Agent VM (`~/.ssh` absent) |
| `SSHPASS` fallback | **Not set** (correct — passwords must not be committed) |
| Jump → lab reachability | **Not tested** (auth unavailable) |

## Last evidence already in git

| Pilot | Last day note | Last `soak-latest.json` date | Calendar expectation on 2026-09-12 |
|-------|---------------|------------------------------|-----------------------------------|
| #1 Modbus | [day4](2026-09-09-bl140-pilot1-day4.md) | 2026-09-09 | ~day **7** |
| #2 MQTT | [day2](2026-09-09-bl140-pilot2-mqtt-day2.md) | 2026-09-09 | ~day **5** |
| #3 OPC UA | [day2](2026-09-09-bl140-pilot3-opcua-day2.md) | 2026-09-09 | ~day **5** |

Timers were armed 06:00 / 06:05 / 06:10 MSK ([parallel timers](2026-09-08-bl140-parallel-timers.md)). Whether they still fire is **unknown** until SSH is restored.

## Unblock

1. Install jump key on the agent host as `~/.ssh/lab_ed25519` (mode `600`), **or** set `ISPF_LAB_SSH_IDENTITY_FILE` to the key path / inject Cloud Agent secret `ISPF_LAB_SSH_PRIVATE_KEY` into the environment.
2. Re-run:

```bash
./tools/ot-trust/pull-pilot-soak-evidence.sh
```

3. Append day notes + journal rows; only then claim day 5–7 / close-out.

## Honesty

- This note is an **access gap**, not a soak failure and not a soak pass.
- Does **not** change OT scorecard / BL-140 field Done.
- Lab VLAN soak ≠ customer plant; not OT **10/10**.
