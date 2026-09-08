# BL-140 parallel ops — timers + Mosquitto harden (2026-09-08)

| Item | Result |
|------|--------|
| Mosquitto `ispf-loadgen-mqtt-1` | `restart=unless-stopped` |
| Pilot #2 soak timer | `ispf-pilot2-soak-check.timer` **06:05 MSK** enabled |
| Pilot #3 soak timer | `ispf-pilot3-soak-check.timer` **06:10 MSK** enabled |
| Pilot #1 timer | unchanged **06:00 MSK** |
| Mid-day Pilot #2 health | pass after republish (`pilot2-lab/soak-health-2026-09-08.json`) |
| OPC UA packs | rebuilt/replaced on lab (signature fix); ISPF restarted; pilots re-started |

Next calendar soak pull: 2026-09-09 ~06:00–06:10 MSK (Pilot #1 day 4, Pilot #2 day 2, Pilot #3 day 2).
