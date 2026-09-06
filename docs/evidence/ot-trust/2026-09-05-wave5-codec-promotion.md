# OT Trust Wave 5 — clean-room codec promotion

Date: 2026-09-05

## Result

| Metric | After Wave 4 | After Wave 5 |
|--------|--------------|--------------|
| Matrix ENTRIES | 103 | **115** |
| Stub list | 59 | **47** |
| READY_LAB | 100 | **112** |
| FAIL / WARN | 0 / 0 | **0 / 0** |

## Promoted packs (15)

| driverId | notes |
|----------|-------|
| camera-ai | HTTP/1.1 inference lab |
| dali | DALI ASCII gateway lab |
| canbus-gateway | CAN TCP gateway lab |
| j1939 | J1939-over-TCP gateway lab |
| codesys | CODESYS text gateway lab |
| wago | Modbus-TCP FC3/FC6 lab |
| idec-microsmart | Host Link ASCII lab |
| unitronics | PCOM ASCII lab |
| ge-srtp | GE SRTP mailbox lab |
| rockwell-df1 | DF1 protected binary TCP-bridge lab |
| iec103 | IEC 60870-5-103 TCP lab |
| secs-gem | HSMS/SECS-II GEM lab subset |

Plus earlier in-wave: camera-ai/dali/canbus-gateway/j1939/codesys counted above.

## Policy
Apache-2.0 JDK clean-room; high-risk fieldbus/radio/CNC remain stubbed. Lab PRODUCTION ≠ field certification.
