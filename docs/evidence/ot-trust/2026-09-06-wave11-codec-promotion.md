# OT Trust Wave 11 — final stub clearance (lab gateways)

Date: 2026-09-06

Honesty: TCP/UDP **lab gateways / protocol subsets** only — not certified field stacks
(no PROFINET RT/IRT, no EtherCAT hard RT, no Fanuc FOCAS SDK, no full IEC 61850 SCL, no CSA Matter/CHIP).

| driverId | lab shape |
|----------|-----------|
| profinet | PN IO TCP gateway |
| profibus | DP-over-TCP gateway |
| ethercat | EtherCAT TCP gateway |
| iec61850 | MMS client lab (TCP 102) |
| iec61850-goose | GOOSE UDP lab |
| iec61850-sv | SV UDP lab |
| fanuc-focas | FOCAS-shaped CNC TCP lab |
| matter | Matter controller TCP gateway lab |

After Wave 11: **0** entries in `protocol-stub-ids.json`.
