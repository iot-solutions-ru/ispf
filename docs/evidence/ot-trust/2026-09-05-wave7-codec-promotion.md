# OT Trust Wave 7 — clean-room codec promotion

Date: 2026-09-05

Honesty: lab-subset codecs only (JDK / Apache-2.0). Not full vendor stacks,
native fieldbus PHYs, FSK modems, or radio stacks.

## Process / building

| driverId | pack | notes |
|----------|------|-------|
| hart-serial | ispf-driver-hart-serial | TCP serial-gateway HART PDU lab — not FSK modem |
| lonworks | ispf-driver-lonworks | LonTalk-IP / LON-over-TCP NV GET/SET lab — not TP LonTalk |

## Discrete gateways

| driverId | pack | notes |
|----------|------|-------|
| as-interface | ispf-driver-as-interface | AS-i master ASCII TCP gateway — not yellow-cable master |
| io-link | ispf-driver-io-link | JSON-over-TCP master bridge — not IO-Link PHY/ISDU |
| interbus | ispf-driver-interbus | Process-word TCP gateway — not Phoenix ASIC (port 502 ≠ Modbus) |

## Still stubbed (deferred)

High-risk / radio / CNC / IEC 61850* / classic OPC A&E·HDA / OPC UA PubSub /
Profinet·EtherCAT·CC-Link·DeviceNet·ControlNet / WirelessHART·ISA100·Wi-SUN /
LoRaWAN·Zigbee·Matter·Z-Wave·Thread·BLE / Foundation Fieldbus·PROFIBUS PA / EEBus.
