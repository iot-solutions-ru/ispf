# OT Trust Wave 9 — clean-room codec promotion

Date: 2026-09-05

Honesty: TCP/UDP **gateway labs only**. Not native H1/PA/CAN/coax PHYs, not full OPC UA PubSub, not ODVA stacks.

| driverId | pack | notes |
|----------|------|-------|
| foundation-fieldbus | ispf-driver-foundation-fieldbus | FF HSE/TCP ASCII gateway lab |
| profibus-pa | ispf-driver-profibus-pa | PA-over-TCP gateway lab |
| opcua-pubsub | ispf-driver-opcua-pubsub | UADP/UDP lab subset |
| device-net | ispf-driver-device-net | CIP/DeviceNet TCP gateway lab |
| controlnet | ispf-driver-controlnet | ControlNet/CIP TCP gateway lab |

Still deferred: Profinet, EtherCAT, Powerlink, CC-Link*, Fanuc, IEC 61850*, radio (Wi-SUN/WirelessHART/ISA100/LoRaWAN/Zigbee/Matter/Z-Wave/Thread/BLE).
