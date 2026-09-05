# OT Trust Wave 6 — clean-room codec promotion

Date: 2026-09-05

Honesty: all promotions are lab-subset codecs (JDK / Apache-2.0). They are **not**
full vendor stacks, native fieldbus PHYs, or Secure AUTOSAR / HCF / OMS RF products.

## Edge (first batch)

| driverId | pack | notes |
|----------|------|-------|
| enocean | ispf-driver-enocean | ESP3 TCP gateway lab |
| sigfox | ispf-driver-sigfox | HTTP callback lab |
| genicam | ispf-driver-genicam | Feature GET/SET lab |

## PLC west / Asia

| driverId | pack | notes |
|----------|------|-------|
| rockwell-csp | ispf-driver-rockwell-csp | CSP/PCCC lab |
| plcnext | ispf-driver-plcnext | RSC-lab subset |
| schneider-umac | ispf-driver-schneider-umac | Modbus-shaped UMAC lab |
| fuji-sph | ispf-driver-fuji-sph | Host Link–shaped ASCII lab |
| hitachi-hidic | ispf-driver-hitachi-hidic | Host Link–shaped ASCII lab |
| toshiba-t-series | ispf-driver-toshiba-t-series | Host Link–shaped ASCII lab |

## Vehicle

| driverId | pack | notes |
|----------|------|-------|
| canopen | ispf-driver-canopen | SDO GET/SET over TCP gateway — not SocketCAN/CiA |
| uds | ispf-driver-uds | DoIP 0x10/0x22/0x2E lab — not full ISO-TP |
| someip | ispf-driver-someip | Header+payload UDP/TCP — not SD / secure AUTOSAR |

## Building / process

| driverId | pack | notes |
|----------|------|-------|
| hart-ip | ispf-driver-hart-ip | Session + cmd 1/3 PV read — not FSK / full HCF |
| bacnet-mstp | ispf-driver-bacnet-mstp | MS/TP-over-TCP framed APDU — not RS-485 master |
| wmbus | ispf-driver-wmbus | TCP POLL telegram / OMS short frame — not RF PHY |

High-risk fieldbus / radio / CNC / IEC 61850* remain stubbed.
