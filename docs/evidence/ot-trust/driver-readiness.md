# Driver readiness audit (all packs)

> Generated: `2026-09-05T10:40:18Z`  
> Catalog: **162** / expected **162**  
> Matrix ENTRIES: **113** · Stub list: **49**  
> Findings: FAIL **0** · WARN **0**  
> Honesty: Lab/matrix readiness only — not field certification for 162 drivers

## Summary

| Dimension | Counts |
|-----------|--------|
| Maturity | `BETA`=3, `PRODUCTION`=110, `STUB`=49 |
| Readiness | `PARTIAL`=1, `READY_LAB`=110, `SHELL_BETA`=2, `STUB_LAB`=49 |
| Source | `matrix`=113, `stub-list`=49 |

### Readiness legend

| Label | Meaning |
|-------|---------|
| `READY_LAB` | PRODUCTION + source + loopback; no FAIL (lab ≠ field) |
| `SHELL_BETA` | Top-20 BETA shell |
| `PARTIAL` | BETA / incomplete |
| `STUB_LAB` | Protocol stub + pack contract test (loopback; still no codec) |
| `STUB` | Protocol catalog stub without pack contract test |
| `BLOCKED` | Honesty FAIL |

## FAIL findings

_None._

## WARN findings

_None._

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
| `amqp` | `ispf-driver-amqp` | PRODUCTION | matrix | `READY_LAB` | — |
| `ansi-c12` | `ispf-driver-ansi-c12` | PRODUCTION | matrix | `READY_LAB` | — |
| `application` | `ispf-driver-application` | PRODUCTION | matrix | `READY_LAB` | — |
| `as-interface` | `ispf-driver-as-interface` | STUB | stub-list | `STUB_LAB` | — |
| `asterisk` | `ispf-driver-asterisk` | PRODUCTION | matrix | `READY_LAB` | — |
| `aws-iot-core` | `ispf-driver-aws-iot-core` | PRODUCTION | matrix | `READY_LAB` | — |
| `azure-iot-hub` | `ispf-driver-azure-iot-hub` | PRODUCTION | matrix | `READY_LAB` | — |
| `bacnet` | `ispf-driver-bacnet` | PRODUCTION | matrix | `READY_LAB` | — |
| `bacnet-mstp` | `ispf-driver-bacnet-mstp` | STUB | stub-list | `STUB_LAB` | — |
| `barcode-scanner` | `ispf-driver-barcode-scanner` | PRODUCTION | matrix | `READY_LAB` | — |
| `beckhoff-ads` | `ispf-driver-beckhoff-ads` | PRODUCTION | matrix | `READY_LAB` | — |
| `bluetooth-le` | `ispf-driver-bluetooth-le` | STUB | stub-list | `STUB_LAB` | — |
| `camera-ai` | `ispf-driver-camera-ai` | PRODUCTION | matrix | `READY_LAB` | — |
| `canbus-gateway` | `ispf-driver-canbus-gateway` | PRODUCTION | matrix | `READY_LAB` | — |
| `canopen` | `ispf-driver-canopen` | STUB | stub-list | `STUB_LAB` | — |
| `cc-link` | `ispf-driver-cc-link` | STUB | stub-list | `STUB_LAB` | — |
| `cc-link-ie` | `ispf-driver-cc-link-ie` | STUB | stub-list | `STUB_LAB` | — |
| `coap` | `ispf-driver-coap` | PRODUCTION | matrix | `READY_LAB` | — |
| `codesys` | `ispf-driver-codesys` | PRODUCTION | matrix | `READY_LAB` | — |
| `controlnet` | `ispf-driver-controlnet` | STUB | stub-list | `STUB_LAB` | — |
| `corba` | `ispf-driver-corba` | BETA | matrix | `PARTIAL` | — |
| `cwmp` | `ispf-driver-cwmp` | PRODUCTION | matrix | `READY_LAB` | — |
| `dali` | `ispf-driver-dali` | PRODUCTION | matrix | `READY_LAB` | — |
| `delta-dvp` | `ispf-driver-delta-dvp` | PRODUCTION | matrix | `READY_LAB` | — |
| `device-net` | `ispf-driver-device-net` | STUB | stub-list | `STUB_LAB` | — |
| `dhcp` | `ispf-driver-dhcp` | PRODUCTION | matrix | `READY_LAB` | — |
| `dlms` | `ispf-driver-dlms` | PRODUCTION | matrix | `READY_LAB` | — |
| `dnp3` | `ispf-driver-dnp3` | PRODUCTION | matrix | `READY_LAB` | — |
| `eebus` | `ispf-driver-eebus` | STUB | stub-list | `STUB_LAB` | — |
| `email` | `ispf-driver-email` | PRODUCTION | matrix | `READY_LAB` | — |
| `enocean` | `ispf-driver-enocean` | STUB | stub-list | `STUB_LAB` | — |
| `ethercat` | `ispf-driver-ethercat` | STUB | stub-list | `STUB_LAB` | — |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | PRODUCTION | matrix | `READY_LAB` | — |
| `ethernet-powerlink` | `ispf-driver-ethernet-powerlink` | STUB | stub-list | `STUB_LAB` | — |
| `fanuc-focas` | `ispf-driver-fanuc-focas` | STUB | stub-list | `STUB_LAB` | — |
| `fatek` | `ispf-driver-fatek` | PRODUCTION | matrix | `READY_LAB` | — |
| `file` | `ispf-driver-file` | PRODUCTION | matrix | `READY_LAB` | — |
| `flexible` | `ispf-driver-flexible` | PRODUCTION | matrix | `READY_LAB` | — |
| `folder` | `ispf-driver-folder` | PRODUCTION | matrix | `READY_LAB` | — |
| `foundation-fieldbus` | `ispf-driver-foundation-fieldbus` | STUB | stub-list | `STUB_LAB` | — |
| `fuji-sph` | `ispf-driver-fuji-sph` | STUB | stub-list | `STUB_LAB` | — |
| `ge-srtp` | `ispf-driver-ge-srtp` | PRODUCTION | matrix | `READY_LAB` | — |
| `genicam` | `ispf-driver-genicam` | STUB | stub-list | `STUB_LAB` | — |
| `gps-tracker` | `ispf-driver-gps-tracker` | PRODUCTION | matrix | `READY_LAB` | — |
| `graph-db` | `ispf-driver-graph-db` | PRODUCTION | matrix | `READY_LAB` | — |
| `graphql` | `ispf-driver-graphql` | PRODUCTION | matrix | `READY_LAB` | — |
| `grpc` | `ispf-driver-grpc` | PRODUCTION | matrix | `READY_LAB` | — |
| `hart-ip` | `ispf-driver-hart-ip` | STUB | stub-list | `STUB_LAB` | — |
| `hart-serial` | `ispf-driver-hart-serial` | STUB | stub-list | `STUB_LAB` | — |
| `haystack` | `ispf-driver-haystack` | PRODUCTION | matrix | `READY_LAB` | — |
| `hitachi-hidic` | `ispf-driver-hitachi-hidic` | STUB | stub-list | `STUB_LAB` | — |
| `http` | `ispf-driver-http` | PRODUCTION | matrix | `READY_LAB` | — |
| `http-server` | `ispf-driver-http-server` | PRODUCTION | matrix | `READY_LAB` | — |
| `icmp` | `ispf-driver-icmp` | PRODUCTION | matrix | `READY_LAB` | — |
| `idec-microsmart` | `ispf-driver-idec-microsmart` | PRODUCTION | matrix | `READY_LAB` | — |
| `iec101` | `ispf-driver-iec101` | PRODUCTION | matrix | `READY_LAB` | — |
| `iec103` | `ispf-driver-iec103` | STUB | stub-list | `STUB_LAB` | — |
| `iec104` | `ispf-driver-iec104` | PRODUCTION | matrix | `READY_LAB` | — |
| `iec104-server` | `ispf-driver-iec104-server` | PRODUCTION | matrix | `READY_LAB` | — |
| `iec61850` | `ispf-driver-iec61850` | STUB | stub-list | `STUB_LAB` | — |
| `iec61850-goose` | `ispf-driver-iec61850-goose` | STUB | stub-list | `STUB_LAB` | — |
| `iec61850-sv` | `ispf-driver-iec61850-sv` | STUB | stub-list | `STUB_LAB` | — |
| `iec62056` | `ispf-driver-iec62056` | PRODUCTION | matrix | `READY_LAB` | — |
| `ieee2030-5` | `ispf-driver-ieee2030-5` | PRODUCTION | matrix | `READY_LAB` | — |
| `imap` | `ispf-driver-imap` | PRODUCTION | matrix | `READY_LAB` | — |
| `ingress-sflow` | `ispf-driver-ingress-sflow` | PRODUCTION | matrix | `READY_LAB` | — |
| `ingress-snmp-trap` | `ispf-driver-ingress-snmp-trap` | PRODUCTION | matrix | `READY_LAB` | — |
| `ingress-syslog` | `ispf-driver-ingress-syslog` | PRODUCTION | matrix | `READY_LAB` | — |
| `interbus` | `ispf-driver-interbus` | STUB | stub-list | `STUB_LAB` | — |
| `io-link` | `ispf-driver-io-link` | STUB | stub-list | `STUB_LAB` | — |
| `ip-host` | `ispf-driver-ip-host` | PRODUCTION | matrix | `READY_LAB` | — |
| `ipmi` | `ispf-driver-ipmi` | PRODUCTION | matrix | `READY_LAB` | — |
| `isa100` | `ispf-driver-isa100` | STUB | stub-list | `STUB_LAB` | — |
| `j1939` | `ispf-driver-j1939` | PRODUCTION | matrix | `READY_LAB` | — |
| `jdbc` | `ispf-driver-jdbc` | PRODUCTION | matrix | `READY_LAB` | — |
| `jms` | `ispf-driver-jms` | PRODUCTION | matrix | `READY_LAB` | — |
| `jmx` | `ispf-driver-jmx` | PRODUCTION | matrix | `READY_LAB` | — |
| `kafka` | `ispf-driver-kafka` | PRODUCTION | matrix | `READY_LAB` | — |
| `keyence-hostlink` | `ispf-driver-keyence-hostlink` | PRODUCTION | matrix | `READY_LAB` | — |
| `knx` | `ispf-driver-knx` | PRODUCTION | matrix | `READY_LAB` | — |
| `knx-tp` | `ispf-driver-knx-tp` | PRODUCTION | matrix | `READY_LAB` | — |
| `ldap` | `ispf-driver-ldap` | PRODUCTION | matrix | `READY_LAB` | — |
| `lonworks` | `ispf-driver-lonworks` | STUB | stub-list | `STUB_LAB` | — |
| `lorawan` | `ispf-driver-lorawan` | STUB | stub-list | `STUB_LAB` | — |
| `ls-xgt` | `ispf-driver-ls-xgt` | PRODUCTION | matrix | `READY_LAB` | — |
| `lwm2m` | `ispf-driver-lwm2m` | PRODUCTION | matrix | `READY_LAB` | — |
| `matter` | `ispf-driver-matter` | STUB | stub-list | `STUB_LAB` | — |
| `mbus` | `ispf-driver-mbus` | PRODUCTION | matrix | `READY_LAB` | — |
| `message-stream` | `ispf-driver-message-stream` | PRODUCTION | matrix | `READY_LAB` | — |
| `mitsubishi-melsec` | `ispf-driver-mitsubishi-melsec` | PRODUCTION | matrix | `READY_LAB` | — |
| `mitsubishi-slmp` | `ispf-driver-mitsubishi-slmp` | PRODUCTION | matrix | `READY_LAB` | — |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | PRODUCTION | matrix | `READY_LAB` | — |
| `modbus-tcp` | `ispf-driver-modbus` | PRODUCTION | matrix | `READY_LAB` | — |
| `modbus-udp` | `ispf-driver-modbus-udp` | PRODUCTION | matrix | `READY_LAB` | — |
| `modem-at` | `ispf-driver-modem-at` | PRODUCTION | matrix | `READY_LAB` | — |
| `mqtt` | `ispf-driver-mqtt` | PRODUCTION | matrix | `READY_LAB` | — |
| `mqtt-sn` | `ispf-driver-mqtt-sn` | PRODUCTION | matrix | `READY_LAB` | — |
| `mtconnect` | `ispf-driver-mtconnect` | PRODUCTION | matrix | `READY_LAB` | — |
| `nats` | `ispf-driver-nats` | PRODUCTION | matrix | `READY_LAB` | — |
| `nmea` | `ispf-driver-nmea` | PRODUCTION | matrix | `READY_LAB` | — |
| `ocpp` | `ispf-driver-ocpp` | PRODUCTION | matrix | `READY_LAB` | — |
| `odata` | `ispf-driver-odata` | PRODUCTION | matrix | `READY_LAB` | — |
| `odbc` | `ispf-driver-odbc` | PRODUCTION | matrix | `READY_LAB` | — |
| `omron-fins` | `ispf-driver-omron-fins` | PRODUCTION | matrix | `READY_LAB` | — |
| `onvif` | `ispf-driver-onvif` | PRODUCTION | matrix | `READY_LAB` | — |
| `opc-ae` | `ispf-driver-opc-ae` | STUB | stub-list | `STUB_LAB` | — |
| `opc-bridge` | `ispf-driver-opc-bridge` | BETA | matrix | `SHELL_BETA` | — |
| `opc-da` | `ispf-driver-opc-da` | BETA | matrix | `SHELL_BETA` | — |
| `opc-hda` | `ispf-driver-opc-hda` | STUB | stub-list | `STUB_LAB` | — |
| `opcua` | `ispf-driver-opcua` | PRODUCTION | matrix | `READY_LAB` | — |
| `opcua-pubsub` | `ispf-driver-opcua-pubsub` | STUB | stub-list | `STUB_LAB` | — |
| `opcua-server` | `ispf-driver-opcua-server` | PRODUCTION | matrix | `READY_LAB` | — |
| `openadr` | `ispf-driver-openadr` | PRODUCTION | matrix | `READY_LAB` | — |
| `panasonic-mewto` | `ispf-driver-panasonic-mewto` | PRODUCTION | matrix | `READY_LAB` | — |
| `plcnext` | `ispf-driver-plcnext` | STUB | stub-list | `STUB_LAB` | — |
| `pop3` | `ispf-driver-pop3` | PRODUCTION | matrix | `READY_LAB` | — |
| `profibus` | `ispf-driver-profibus` | STUB | stub-list | `STUB_LAB` | — |
| `profibus-pa` | `ispf-driver-profibus-pa` | STUB | stub-list | `STUB_LAB` | — |
| `profinet` | `ispf-driver-profinet` | STUB | stub-list | `STUB_LAB` | — |
| `pulsar` | `ispf-driver-pulsar` | PRODUCTION | matrix | `READY_LAB` | — |
| `radius` | `ispf-driver-radius` | PRODUCTION | matrix | `READY_LAB` | — |
| `redis` | `ispf-driver-redis` | PRODUCTION | matrix | `READY_LAB` | — |
| `rockwell-csp` | `ispf-driver-rockwell-csp` | STUB | stub-list | `STUB_LAB` | — |
| `rockwell-df1` | `ispf-driver-rockwell-df1` | PRODUCTION | matrix | `READY_LAB` | — |
| `rtsp` | `ispf-driver-rtsp` | PRODUCTION | matrix | `READY_LAB` | — |
| `s7` | `ispf-driver-s7` | PRODUCTION | matrix | `READY_LAB` | — |
| `schneider-umac` | `ispf-driver-schneider-umac` | STUB | stub-list | `STUB_LAB` | — |
| `scpi` | `ispf-driver-scpi` | PRODUCTION | matrix | `READY_LAB` | — |
| `secs-gem` | `ispf-driver-secs-gem` | STUB | stub-list | `STUB_LAB` | — |
| `sigfox` | `ispf-driver-sigfox` | STUB | stub-list | `STUB_LAB` | — |
| `sip` | `ispf-driver-sip` | PRODUCTION | matrix | `READY_LAB` | — |
| `smb` | `ispf-driver-smb` | PRODUCTION | matrix | `READY_LAB` | — |
| `smi-s` | `ispf-driver-smis` | PRODUCTION | matrix | `READY_LAB` | — |
| `smpp` | `ispf-driver-smpp` | PRODUCTION | matrix | `READY_LAB` | — |
| `sms` | `ispf-driver-sms` | PRODUCTION | matrix | `READY_LAB` | — |
| `snmp` | `ispf-driver-snmp` | PRODUCTION | matrix | `READY_LAB` | — |
| `soap` | `ispf-driver-soap` | PRODUCTION | matrix | `READY_LAB` | — |
| `someip` | `ispf-driver-someip` | STUB | stub-list | `STUB_LAB` | — |
| `sparkplug-b` | `ispf-driver-sparkplug-b` | PRODUCTION | matrix | `READY_LAB` | — |
| `ssh` | `ispf-driver-ssh` | PRODUCTION | matrix | `READY_LAB` | — |
| `telnet` | `ispf-driver-telnet` | PRODUCTION | matrix | `READY_LAB` | — |
| `thread` | `ispf-driver-thread` | STUB | stub-list | `STUB_LAB` | — |
| `toshiba-t-series` | `ispf-driver-toshiba-t-series` | STUB | stub-list | `STUB_LAB` | — |
| `uds` | `ispf-driver-uds` | STUB | stub-list | `STUB_LAB` | — |
| `unitronics` | `ispf-driver-unitronics` | PRODUCTION | matrix | `READY_LAB` | — |
| `virtual` | `ispf-driver-virtual` | PRODUCTION | matrix | `READY_LAB` | — |
| `visa` | `ispf-driver-visa` | PRODUCTION | matrix | `READY_LAB` | — |
| `vmware` | `ispf-driver-vmware` | PRODUCTION | matrix | `READY_LAB` | — |
| `wago` | `ispf-driver-wago` | PRODUCTION | matrix | `READY_LAB` | — |
| `weather-station` | `ispf-driver-weather-station` | PRODUCTION | matrix | `READY_LAB` | — |
| `web-transaction` | `ispf-driver-web-transaction` | PRODUCTION | matrix | `READY_LAB` | — |
| `webhook` | `ispf-driver-webhook` | PRODUCTION | matrix | `READY_LAB` | — |
| `websocket` | `ispf-driver-websocket` | PRODUCTION | matrix | `READY_LAB` | — |
| `weighbridge` | `ispf-driver-weighbridge` | PRODUCTION | matrix | `READY_LAB` | — |
| `wirelesshart` | `ispf-driver-wirelesshart` | STUB | stub-list | `STUB_LAB` | — |
| `wisun` | `ispf-driver-wisun` | STUB | stub-list | `STUB_LAB` | — |
| `wmbus` | `ispf-driver-wmbus` | STUB | stub-list | `STUB_LAB` | — |
| `wmi` | `ispf-driver-wmi` | PRODUCTION | matrix | `READY_LAB` | — |
| `xmpp` | `ispf-driver-xmpp` | PRODUCTION | matrix | `READY_LAB` | — |
| `yaskawa-memobus` | `ispf-driver-yaskawa-memobus` | PRODUCTION | matrix | `READY_LAB` | — |
| `zigbee` | `ispf-driver-zigbee` | STUB | stub-list | `STUB_LAB` | — |
| `zwave` | `ispf-driver-zwave` | STUB | stub-list | `STUB_LAB` | — |

## How to re-run

```bash
python3 tools/driver-readiness-audit.py \
  --md docs/evidence/ot-trust/driver-readiness.md \
  --json docs/evidence/ot-trust/driver-readiness.json \
  --fail-on-findings
```
