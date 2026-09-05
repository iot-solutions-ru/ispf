# OT Trust Wave 5 — clean-room codec promotion (in progress)

Date: 2026-09-05

## First batch (edge / gateway)

| driverId | pack | notes |
|----------|------|-------|
| camera-ai | ispf-driver-camera-ai | HTTP inference lab |
| dali | ispf-driver-dali | DALI ASCII gateway lab |
| canbus-gateway | ispf-driver-canbus-gateway | CAN TCP gateway lab |

Parallel agents still promoting GE SRTP, DF1, WAGO/IDEC/Unitronics, IEC103/SECS-GEM, J1939/CODESYS.

## Policy
Apache-2.0 JDK clean-room; high-risk fieldbus/radio/CNC left stubbed.

| j1939 | ispf-driver-j1939 | TCP PGN gateway lab |
| codesys | ispf-driver-codesys | text GET/SET gateway lab |
| wago | ispf-driver-wago | Modbus-TCP lab |
| idec-microsmart | ispf-driver-idec-microsmart | Host Link ASCII lab |
| unitronics | ispf-driver-unitronics | PCOM ASCII lab |
| ge-srtp | ispf-driver-ge-srtp | SRTP lab |
| rockwell-df1 | ispf-driver-rockwell-df1 | DF1 TCP-bridge lab |

