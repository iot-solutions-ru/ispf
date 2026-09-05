# OT Trust Wave 10 — clean-room codec promotion

Date: 2026-09-05

Honesty: TCP/UDP **gateway labs only**. Not RF PHYs, not CLPA/EPSG hard-RT stacks, not CHIP/Matter.

## LPWAN / process wireless
| driverId | notes |
|----------|-------|
| lorawan | NS/AS or packet-forwarder TCP lab |
| wisun | CoAP border-router lab |
| wirelesshart | WirelessHART gateway TCP lab |
| isa100 | ISA100 gateway TCP lab |

## PAN / mesh gateways
| driverId | notes |
|----------|-------|
| bluetooth-le | GATT-over-TCP gateway |
| zigbee | ZCL coordinator TCP gateway |
| zwave | Z-Wave controller TCP gateway |
| thread | Thread BR TCP gateway |

## Industrial Ethernet labs
| driverId | notes |
|----------|-------|
| cc-link | SLMP/ASCII TCP gateway |
| cc-link-ie | CC-Link IE Field TCP gateway |
| ethernet-powerlink | POWERLINK UDP PDO-ish lab |

## Still stubbed
profinet, profibus, ethercat, fanuc-focas, iec61850*, matter.
