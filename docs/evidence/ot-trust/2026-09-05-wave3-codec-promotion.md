# OT Trust Wave 3 — clean-room codec promotions

> Date: 2026-09-05  
> Honesty: lab PRODUCTION (loopback) only — not field certification.  
> License: Apache-2.0 clean-room / JDK-only (no GPL, no proprietary SDKs).

## Promoted to PRODUCTION (13)

| driverId | Caps | Notes |
|----------|------|-------|
| beckhoff-ads | POLL+WRITE | AMS/TCP IG:IO subset |
| mitsubishi-melsec | POLL+WRITE | MC/SLMP 3E D-registers |
| iec62056 | POLL | IEC 62056-21 Mode C readout |
| ieee2030-5 | POLL | SEP2 HTTP/XML GET subset |
| mqtt-sn | POLL+WRITE | MQTT-SN 1.2 UDP subset |
| nats | POLL+WRITE | NATS text protocol subset |
| pulsar | POLL+WRITE | Lab TCP text framing (not binary Pulsar) |
| onvif | POLL+WRITE | SOAP GetDeviceInformation subset |
| mtconnect | POLL | Agent HTTP current streams |
| knx | POLL+WRITE | KNXnet/IP tunneling group R/W |
| lwm2m | POLL | CoAP GET resource subset |
| websocket | POLL+WRITE | RFC6455 text frames |
| graphql | POLL+WRITE | HTTP GraphQL query/mutation |

## Wave 3b follow-up (+7)

| driverId | Caps | Notes |
|----------|------|-------|
| ocpp | POLL+WRITE | OCPP 1.6 JSON-lines TCP lab CSMS subset |
| odata | POLL+WRITE | OData JSON v4 HTTP subset |
| grpc | POLL+WRITE | Honest **gRPC-JSON lab** (not wire gRPC) |
| openadr | POLL+WRITE | OpenADR 2.0b VEN poll subset |
| scpi | POLL+WRITE | IEEE 488.2 SCPI over TCP |
| visa | POLL+WRITE | SOCKET-only SCPI-over-TCP (not NI-VISA) |
| knx-tp | POLL+WRITE | KNXnet/IP Routing cEMI (“TP via IP”) |

## Explicitly deferred (still STUB / high-risk)

- High patent/license risk left untouched: `profinet`, `ethercat`, `iec61850*`, `fanuc-focas`, `lorawan`, classic fieldbus.

## Catalog after Wave 3

- Matrix PRODUCTION grows by +13 (Wave 2 had +4).
- Stub catalog 93 → 73 after Wave 3b.
- Matrix PRODUCTION 66 → 86 (Wave2+3+3b).
