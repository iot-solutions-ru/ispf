# Driver readiness audit (all packs)

> Generated: `2026-09-05T07:52:46Z`  
> Catalog: **162** / expected **162**  
> Matrix ENTRIES: **65** · Stub list: **97**  
> Findings: FAIL **0** · WARN **3**  
> Honesty: Lab/matrix readiness only — not field certification for 162 drivers

## Summary

| Dimension | Counts |
|-----------|--------|
| Maturity | `BETA`=3, `PRODUCTION`=62, `STUB`=97 |
| Readiness | `PARTIAL`=1, `READY_LAB`=62, `SHELL_BETA`=2, `STUB`=97 |
| Source | `matrix`=65, `stub-list`=97 |

### Readiness legend

| Label | Meaning |
|-------|---------|
| `READY_LAB` | PRODUCTION + source + loopback; no FAIL (lab ≠ field) |
| `SHELL_BETA` | Top-20 BETA shell |
| `PARTIAL` | BETA / incomplete |
| `STUB` | Protocol catalog stub |
| `BLOCKED` | Honesty FAIL |

## FAIL findings

_None._

## WARN findings

| driverId | code | detail |
|----------|------|--------|
| `ingress-sflow` | `WRITE_UNDERCLAIM` | writePoint looks real but matrix has no WRITE |
| `ingress-snmp-trap` | `WRITE_UNDERCLAIM` | writePoint looks real but matrix has no WRITE |
| `ingress-syslog` | `WRITE_UNDERCLAIM` | writePoint looks real but matrix has no WRITE |

## Top-20 industrial

| driverId | maturity | readiness | WRITE | loopback | interop |
|----------|----------|-----------|-------|----------|---------|
| `bacnet` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `dlms` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `dnp3` | PRODUCTION | `READY_LAB` | n | ✓ | ✓ |
| `ethernet-ip` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `flexible` | PRODUCTION | `READY_LAB` | n | ✓ | ✓ |
| `gps-tracker` | PRODUCTION | `READY_LAB` | n | ✓ | ✓ |
| `http` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `iec104` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `iec104-server` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `modbus-rtu` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `modbus-tcp` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `modbus-udp` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `mqtt` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `opc-bridge` | BETA | `SHELL_BETA` | n | ✓ | ✓ |
| `opc-da` | BETA | `SHELL_BETA` | n | ✓ | ✓ |
| `opcua` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `opcua-server` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `s7` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `snmp` | PRODUCTION | `READY_LAB` | Y | ✓ | ✓ |
| `virtual` | PRODUCTION | `READY_LAB` | n | ✓ | ✓ |

## Full catalog (162)

| driverId | pack | maturity | source | readiness | FAIL/WARN |
|----------|------|----------|--------|-----------|----------|
| `amqp` | `ispf-driver-amqp` | STUB | stub-list | `STUB` | — |
| `ansi-c12` | `ispf-driver-ansi-c12` | STUB | stub-list | `STUB` | — |
| `application` | `ispf-driver-application` | PRODUCTION | matrix | `READY_LAB` | — |
| `as-interface` | `ispf-driver-as-interface` | STUB | stub-list | `STUB` | — |
| `asterisk` | `ispf-driver-asterisk` | PRODUCTION | matrix | `READY_LAB` | — |
| `aws-iot-core` | `ispf-driver-aws-iot-core` | STUB | stub-list | `STUB` | — |
| `azure-iot-hub` | `ispf-driver-azure-iot-hub` | STUB | stub-list | `STUB` | — |
| `bacnet` | `ispf-driver-bacnet` | PRODUCTION | matrix | `READY_LAB` | — |
| `bacnet-mstp` | `ispf-driver-bacnet-mstp` | STUB | stub-list | `STUB` | — |
| `barcode-scanner` | `ispf-driver-barcode-scanner` | STUB | stub-list | `STUB` | — |
| `beckhoff-ads` | `ispf-driver-beckhoff-ads` | STUB | stub-list | `STUB` | — |
| `bluetooth-le` | `ispf-driver-bluetooth-le` | STUB | stub-list | `STUB` | — |
| `camera-ai` | `ispf-driver-camera-ai` | STUB | stub-list | `STUB` | — |
| `canbus-gateway` | `ispf-driver-canbus-gateway` | STUB | stub-list | `STUB` | — |
| `canopen` | `ispf-driver-canopen` | STUB | stub-list | `STUB` | — |
| `cc-link` | `ispf-driver-cc-link` | STUB | stub-list | `STUB` | — |
| `cc-link-ie` | `ispf-driver-cc-link-ie` | STUB | stub-list | `STUB` | — |
| `coap` | `ispf-driver-coap` | PRODUCTION | matrix | `READY_LAB` | — |
| `codesys` | `ispf-driver-codesys` | STUB | stub-list | `STUB` | — |
| `controlnet` | `ispf-driver-controlnet` | STUB | stub-list | `STUB` | — |
| `corba` | `ispf-driver-corba` | BETA | matrix | `PARTIAL` | — |
| `cwmp` | `ispf-driver-cwmp` | PRODUCTION | matrix | `READY_LAB` | — |
| `dali` | `ispf-driver-dali` | STUB | stub-list | `STUB` | — |
| `delta-dvp` | `ispf-driver-delta-dvp` | STUB | stub-list | `STUB` | — |
| `device-net` | `ispf-driver-device-net` | STUB | stub-list | `STUB` | — |
| `dhcp` | `ispf-driver-dhcp` | PRODUCTION | matrix | `READY_LAB` | — |
| `dlms` | `ispf-driver-dlms` | PRODUCTION | matrix | `READY_LAB` | — |
| `dnp3` | `ispf-driver-dnp3` | PRODUCTION | matrix | `READY_LAB` | — |
| `eebus` | `ispf-driver-eebus` | STUB | stub-list | `STUB` | — |
| `email` | `ispf-driver-email` | PRODUCTION | matrix | `READY_LAB` | — |
| `enocean` | `ispf-driver-enocean` | STUB | stub-list | `STUB` | — |
| `ethercat` | `ispf-driver-ethercat` | STUB | stub-list | `STUB` | — |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | PRODUCTION | matrix | `READY_LAB` | — |
| `ethernet-powerlink` | `ispf-driver-ethernet-powerlink` | STUB | stub-list | `STUB` | — |
| `fanuc-focas` | `ispf-driver-fanuc-focas` | STUB | stub-list | `STUB` | — |
| `fatek` | `ispf-driver-fatek` | STUB | stub-list | `STUB` | — |
| `file` | `ispf-driver-file` | PRODUCTION | matrix | `READY_LAB` | — |
| `flexible` | `ispf-driver-flexible` | PRODUCTION | matrix | `READY_LAB` | — |
| `folder` | `ispf-driver-folder` | PRODUCTION | matrix | `READY_LAB` | — |
| `foundation-fieldbus` | `ispf-driver-foundation-fieldbus` | STUB | stub-list | `STUB` | — |
| `fuji-sph` | `ispf-driver-fuji-sph` | STUB | stub-list | `STUB` | — |
| `ge-srtp` | `ispf-driver-ge-srtp` | STUB | stub-list | `STUB` | — |
| `genicam` | `ispf-driver-genicam` | STUB | stub-list | `STUB` | — |
| `gps-tracker` | `ispf-driver-gps-tracker` | PRODUCTION | matrix | `READY_LAB` | — |
| `graph-db` | `ispf-driver-graph-db` | PRODUCTION | matrix | `READY_LAB` | — |
| `graphql` | `ispf-driver-graphql` | STUB | stub-list | `STUB` | — |
| `grpc` | `ispf-driver-grpc` | STUB | stub-list | `STUB` | — |
| `hart-ip` | `ispf-driver-hart-ip` | STUB | stub-list | `STUB` | — |
| `hart-serial` | `ispf-driver-hart-serial` | STUB | stub-list | `STUB` | — |
| `haystack` | `ispf-driver-haystack` | PRODUCTION | matrix | `READY_LAB` | — |
| `hitachi-hidic` | `ispf-driver-hitachi-hidic` | STUB | stub-list | `STUB` | — |
| `http` | `ispf-driver-http` | PRODUCTION | matrix | `READY_LAB` | — |
| `http-server` | `ispf-driver-http-server` | PRODUCTION | matrix | `READY_LAB` | — |
| `icmp` | `ispf-driver-icmp` | PRODUCTION | matrix | `READY_LAB` | — |
| `idec-microsmart` | `ispf-driver-idec-microsmart` | STUB | stub-list | `STUB` | — |
| `iec101` | `ispf-driver-iec101` | STUB | stub-list | `STUB` | — |
| `iec103` | `ispf-driver-iec103` | STUB | stub-list | `STUB` | — |
| `iec104` | `ispf-driver-iec104` | PRODUCTION | matrix | `READY_LAB` | — |
| `iec104-server` | `ispf-driver-iec104-server` | PRODUCTION | matrix | `READY_LAB` | — |
| `iec61850` | `ispf-driver-iec61850` | STUB | stub-list | `STUB` | — |
| `iec61850-goose` | `ispf-driver-iec61850-goose` | STUB | stub-list | `STUB` | — |
| `iec61850-sv` | `ispf-driver-iec61850-sv` | STUB | stub-list | `STUB` | — |
| `iec62056` | `ispf-driver-iec62056` | STUB | stub-list | `STUB` | — |
| `ieee2030-5` | `ispf-driver-ieee2030-5` | STUB | stub-list | `STUB` | — |
| `imap` | `ispf-driver-imap` | PRODUCTION | matrix | `READY_LAB` | — |
| `ingress-sflow` | `ispf-driver-ingress-sflow` | PRODUCTION | matrix | `READY_LAB` | WRITE_UNDERCLAIM |
| `ingress-snmp-trap` | `ispf-driver-ingress-snmp-trap` | PRODUCTION | matrix | `READY_LAB` | WRITE_UNDERCLAIM |
| `ingress-syslog` | `ispf-driver-ingress-syslog` | PRODUCTION | matrix | `READY_LAB` | WRITE_UNDERCLAIM |
| `interbus` | `ispf-driver-interbus` | STUB | stub-list | `STUB` | — |
| `io-link` | `ispf-driver-io-link` | STUB | stub-list | `STUB` | — |
| `ip-host` | `ispf-driver-ip-host` | PRODUCTION | matrix | `READY_LAB` | — |
| `ipmi` | `ispf-driver-ipmi` | PRODUCTION | matrix | `READY_LAB` | — |
| `isa100` | `ispf-driver-isa100` | STUB | stub-list | `STUB` | — |
| `j1939` | `ispf-driver-j1939` | STUB | stub-list | `STUB` | — |
| `jdbc` | `ispf-driver-jdbc` | PRODUCTION | matrix | `READY_LAB` | — |
| `jms` | `ispf-driver-jms` | PRODUCTION | matrix | `READY_LAB` | — |
| `jmx` | `ispf-driver-jmx` | PRODUCTION | matrix | `READY_LAB` | — |
| `kafka` | `ispf-driver-kafka` | PRODUCTION | matrix | `READY_LAB` | — |
| `keyence-hostlink` | `ispf-driver-keyence-hostlink` | STUB | stub-list | `STUB` | — |
| `knx` | `ispf-driver-knx` | STUB | stub-list | `STUB` | — |
| `knx-tp` | `ispf-driver-knx-tp` | STUB | stub-list | `STUB` | — |
| `ldap` | `ispf-driver-ldap` | PRODUCTION | matrix | `READY_LAB` | — |
| `lonworks` | `ispf-driver-lonworks` | STUB | stub-list | `STUB` | — |
| `lorawan` | `ispf-driver-lorawan` | STUB | stub-list | `STUB` | — |
| `ls-xgt` | `ispf-driver-ls-xgt` | STUB | stub-list | `STUB` | — |
| `lwm2m` | `ispf-driver-lwm2m` | STUB | stub-list | `STUB` | — |
| `matter` | `ispf-driver-matter` | STUB | stub-list | `STUB` | — |
| `mbus` | `ispf-driver-mbus` | PRODUCTION | matrix | `READY_LAB` | — |
| `message-stream` | `ispf-driver-message-stream` | PRODUCTION | matrix | `READY_LAB` | — |
| `mitsubishi-melsec` | `ispf-driver-mitsubishi-melsec` | STUB | stub-list | `STUB` | — |
| `mitsubishi-slmp` | `ispf-driver-mitsubishi-slmp` | STUB | stub-list | `STUB` | — |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | PRODUCTION | matrix | `READY_LAB` | — |
| `modbus-tcp` | `ispf-driver-modbus` | PRODUCTION | matrix | `READY_LAB` | — |
| `modbus-udp` | `ispf-driver-modbus-udp` | PRODUCTION | matrix | `READY_LAB` | — |
| `modem-at` | `ispf-driver-modem-at` | PRODUCTION | matrix | `READY_LAB` | — |
| `mqtt` | `ispf-driver-mqtt` | PRODUCTION | matrix | `READY_LAB` | — |
| `mqtt-sn` | `ispf-driver-mqtt-sn` | STUB | stub-list | `STUB` | — |
| `mtconnect` | `ispf-driver-mtconnect` | STUB | stub-list | `STUB` | — |
| `nats` | `ispf-driver-nats` | STUB | stub-list | `STUB` | — |
| `nmea` | `ispf-driver-nmea` | PRODUCTION | matrix | `READY_LAB` | — |
| `ocpp` | `ispf-driver-ocpp` | STUB | stub-list | `STUB` | — |
| `odata` | `ispf-driver-odata` | STUB | stub-list | `STUB` | — |
| `odbc` | `ispf-driver-odbc` | PRODUCTION | matrix | `READY_LAB` | — |
| `omron-fins` | `ispf-driver-omron-fins` | PRODUCTION | matrix | `READY_LAB` | — |
| `onvif` | `ispf-driver-onvif` | STUB | stub-list | `STUB` | — |
| `opc-ae` | `ispf-driver-opc-ae` | STUB | stub-list | `STUB` | — |
| `opc-bridge` | `ispf-driver-opc-bridge` | BETA | matrix | `SHELL_BETA` | — |
| `opc-da` | `ispf-driver-opc-da` | BETA | matrix | `SHELL_BETA` | — |
| `opc-hda` | `ispf-driver-opc-hda` | STUB | stub-list | `STUB` | — |
| `opcua` | `ispf-driver-opcua` | PRODUCTION | matrix | `READY_LAB` | — |
| `opcua-pubsub` | `ispf-driver-opcua-pubsub` | STUB | stub-list | `STUB` | — |
| `opcua-server` | `ispf-driver-opcua-server` | PRODUCTION | matrix | `READY_LAB` | — |
| `openadr` | `ispf-driver-openadr` | STUB | stub-list | `STUB` | — |
| `panasonic-mewto` | `ispf-driver-panasonic-mewto` | STUB | stub-list | `STUB` | — |
| `plcnext` | `ispf-driver-plcnext` | STUB | stub-list | `STUB` | — |
| `pop3` | `ispf-driver-pop3` | PRODUCTION | matrix | `READY_LAB` | — |
| `profibus` | `ispf-driver-profibus` | STUB | stub-list | `STUB` | — |
| `profibus-pa` | `ispf-driver-profibus-pa` | STUB | stub-list | `STUB` | — |
| `profinet` | `ispf-driver-profinet` | STUB | stub-list | `STUB` | — |
| `pulsar` | `ispf-driver-pulsar` | STUB | stub-list | `STUB` | — |
| `radius` | `ispf-driver-radius` | PRODUCTION | matrix | `READY_LAB` | — |
| `redis` | `ispf-driver-redis` | STUB | stub-list | `STUB` | — |
| `rockwell-csp` | `ispf-driver-rockwell-csp` | STUB | stub-list | `STUB` | — |
| `rockwell-df1` | `ispf-driver-rockwell-df1` | STUB | stub-list | `STUB` | — |
| `rtsp` | `ispf-driver-rtsp` | STUB | stub-list | `STUB` | — |
| `s7` | `ispf-driver-s7` | PRODUCTION | matrix | `READY_LAB` | — |
| `schneider-umac` | `ispf-driver-schneider-umac` | STUB | stub-list | `STUB` | — |
| `scpi` | `ispf-driver-scpi` | STUB | stub-list | `STUB` | — |
| `secs-gem` | `ispf-driver-secs-gem` | STUB | stub-list | `STUB` | — |
| `sigfox` | `ispf-driver-sigfox` | STUB | stub-list | `STUB` | — |
| `sip` | `ispf-driver-sip` | PRODUCTION | matrix | `READY_LAB` | — |
| `smb` | `ispf-driver-smb` | PRODUCTION | matrix | `READY_LAB` | — |
| `smi-s` | `ispf-driver-smis` | PRODUCTION | matrix | `READY_LAB` | — |
| `smpp` | `ispf-driver-smpp` | PRODUCTION | matrix | `READY_LAB` | — |
| `sms` | `ispf-driver-sms` | PRODUCTION | matrix | `READY_LAB` | — |
| `snmp` | `ispf-driver-snmp` | PRODUCTION | matrix | `READY_LAB` | — |
| `soap` | `ispf-driver-soap` | PRODUCTION | matrix | `READY_LAB` | — |
| `someip` | `ispf-driver-someip` | STUB | stub-list | `STUB` | — |
| `sparkplug-b` | `ispf-driver-sparkplug-b` | STUB | stub-list | `STUB` | — |
| `ssh` | `ispf-driver-ssh` | PRODUCTION | matrix | `READY_LAB` | — |
| `telnet` | `ispf-driver-telnet` | PRODUCTION | matrix | `READY_LAB` | — |
| `thread` | `ispf-driver-thread` | STUB | stub-list | `STUB` | — |
| `toshiba-t-series` | `ispf-driver-toshiba-t-series` | STUB | stub-list | `STUB` | — |
| `uds` | `ispf-driver-uds` | STUB | stub-list | `STUB` | — |
| `unitronics` | `ispf-driver-unitronics` | STUB | stub-list | `STUB` | — |
| `virtual` | `ispf-driver-virtual` | PRODUCTION | matrix | `READY_LAB` | — |
| `visa` | `ispf-driver-visa` | STUB | stub-list | `STUB` | — |
| `vmware` | `ispf-driver-vmware` | PRODUCTION | matrix | `READY_LAB` | — |
| `wago` | `ispf-driver-wago` | STUB | stub-list | `STUB` | — |
| `weather-station` | `ispf-driver-weather-station` | STUB | stub-list | `STUB` | — |
| `web-transaction` | `ispf-driver-web-transaction` | PRODUCTION | matrix | `READY_LAB` | — |
| `webhook` | `ispf-driver-webhook` | PRODUCTION | matrix | `READY_LAB` | — |
| `websocket` | `ispf-driver-websocket` | STUB | stub-list | `STUB` | — |
| `weighbridge` | `ispf-driver-weighbridge` | STUB | stub-list | `STUB` | — |
| `wirelesshart` | `ispf-driver-wirelesshart` | STUB | stub-list | `STUB` | — |
| `wisun` | `ispf-driver-wisun` | STUB | stub-list | `STUB` | — |
| `wmbus` | `ispf-driver-wmbus` | STUB | stub-list | `STUB` | — |
| `wmi` | `ispf-driver-wmi` | PRODUCTION | matrix | `READY_LAB` | — |
| `xmpp` | `ispf-driver-xmpp` | PRODUCTION | matrix | `READY_LAB` | — |
| `yaskawa-memobus` | `ispf-driver-yaskawa-memobus` | STUB | stub-list | `STUB` | — |
| `zigbee` | `ispf-driver-zigbee` | STUB | stub-list | `STUB` | — |
| `zwave` | `ispf-driver-zwave` | STUB | stub-list | `STUB` | — |

## How to re-run

```bash
python3 tools/driver-readiness-audit.py \
  --md docs/evidence/ot-trust/driver-readiness.md \
  --json docs/evidence/ot-trust/driver-readiness.json \
  --fail-on-findings
```
