## Зарегистрированный каталог драйверов (163)

Поле `maturity` в `GET /api/v1/drivers`: `PRODUCTION` (по умолчанию), `BETA`, `STUB`. Метки задаются в `DriverMaturityRegistry` на сервере и отображаются в Web Console при выборе драйвера.

Поле `capabilities` — строковый набор из `DriverProductionMatrix` (ADR-0022): `read`, `write`, `subscribe`, `discovery`, `observed_at`, `quality`. Пример: `opcua` → `read`, `write`, `subscribe`, `discovery`, `observed_at`.

`evidence` — `LOOPBACK_VERIFIED` или `FIELD`. `FIELD` — именной заводской допуск; этот набор пуст, поэтому каждый драйвер каталога — `LOOPBACK_VERIFIED`. `readOnly` истинен, когда нет `write`. Веб-консоль показывает оба признака рядом с бейджем зрелости. `list_drivers` возвращает те же поля.

### Продвижение stub (по запросу)

58 основных `driverId` в production-матрице; **97** дополнительных stub-протоколов — отдельные пакеты `ispf-driver-<id>` (**STUB**, только TCP, **Apache-2.0**). Продвижение до **PRODUCTION** — **не** по расписанию roadmap, а **по запросу команды приложения** через gate [0002-dogfooding-gate](decisions/0002-dogfooding-gate.md):

1. Команда приложения описывает сценарий (устройство, маппинг точек, приёмочный тест).
2. PR платформы добавляет протокольную логику (замена stub-класса в `ispf-driver-<id>`).
3. Обновляется `DriverMaturityRegistry` / список stub id; документация в этом файле.

Текущие кандидаты STUB/BETA:

| `driverId` | Зрелость | Примечание |
|------------|----------|---------|
| `corba` | BETA | CORBA IIOP TCP shell — нужна сторонняя ORB |
| `opc-da`, `opc-bridge` | BETA | Classic OPC shells — предпочтительнее `opcua` или внешний DA→UA |
| Каталог протоколов (`sparkplug-b`, `iec61850`, `profinet`, `beckhoff-ads`, `knx`, `lorawan`, …) | STUB | Генерация: один pack на id из `tools/driver-stubs/protocol-stubs.yaml` (база `ispf-driver-stub-kit`) |
| `vmware` | PRODUCTION | vSphere SOAP: Login + RetrieveProperties |
| `smi-s` | PRODUCTION | парсинг SMI-S CIM-XML |

Loopback-тесты (BL-26): `EthernetIpDeviceDriverTest`, `OpcDaDeviceDriverTest`, `OpcBridgeDeviceDriverTest`, `CorbaDeviceDriverTest`, `VmwareDeviceDriverTest` (`useHttp`), `SmisDeviceDriverTest` (`useHttp`).

См. [ROADMAP.md § Phase 17.4](roadmap.md).

### Полный каталог `driverId`

Что делает каждый драйвер (все packs из `gradle/driver-packs.json`):

| `driverId` | Модуль | Зрелость | Лицензия | Назначение |
|------------|--------|----------|----------|------------|
| `amqp` | `ispf-driver-amqp` | PRODUCTION | Apache-2.0 | AMQP 0-9-1 subset (header/start/tune/open/channel/basic.publish+get) — |
| `ansi-c12` | `ispf-driver-ansi-c12` | PRODUCTION | Apache-2.0 | ANSI C12 meter lab over TCP (logon + standard tables); |
| `application` | `ispf-driver-application` | PRODUCTION | Apache-2.0 | Локальные shell/скрипты → переменные ISPF |
| `as-interface` | `ispf-driver-as-interface` | PRODUCTION | Apache-2.0 | AS-Interface master call over TCP gateway; not AS-i PHY |
| `asterisk` | `ispf-driver-asterisk` | PRODUCTION | Apache-2.0 | Asterisk Manager Interface (AMI) |
| `aws-iot-core` | `ispf-driver-aws-iot-core` | BETA | Apache-2.0 | MQTT 3.1.1 client with AWS IoT–shaped topic mappings (not full IoT Core SDK / SigV4) |
| `azure-iot-hub` | `ispf-driver-azure-iot-hub` | BETA | Apache-2.0 | MQTT 3.1.1 client with Azure IoT Hub topic conventions (not full IoT Hub SDK) |
| `bacnet` | `ispf-driver-bacnet` | PRODUCTION | Apache-2.0 | BACnet/IP клиент (clean-room codec) |
| `bacnet-mstp` | `ispf-driver-bacnet-mstp` | PRODUCTION | Apache-2.0 | BACnet MS/TP clause-9 over TCP serial-server: frame type 5 Read/WriteProperty; |
| `barcode-scanner` | `ispf-driver-barcode-scanner` | PRODUCTION | Apache-2.0 | TCP newline-terminated scan stream: last-scan reads, value+CRLF writes |
| `beckhoff-ads` | `ispf-driver-beckhoff-ads` | PRODUCTION | Apache-2.0 | Reads/writes ADS variables by indexGroup/indexOffset over AMS/TCP (INT/DINT/REAL/STRING) |
| `bluetooth-le` | `ispf-driver-bluetooth-le` | PRODUCTION | Apache-2.0 | H4 HCI commands over TCP; not a BLE radio and not a full GATT client; |
| `camera-ai` | `ispf-driver-camera-ai` | PRODUCTION | Apache-2.0 | HTTP/1.1 client for edge vision/AI inference endpoints (GET read / POST write; no TLS) |
| `canbus-gateway` | `ispf-driver-canbus-gateway` | PRODUCTION | Apache-2.0 | CAN via LAWICEL SLCAN over TCP (standard t frames); |
| `canopen` | `ispf-driver-canopen` | PRODUCTION | Apache-2.0 | CANopen SDO via LAWICEL SLCAN over TCP (standard t frames); |
| `cc-link` | `ispf-driver-cc-link` | PRODUCTION | Apache-2.0 | MELSEC SLMP 3E binary (MC protocol) over TCP for D/R/W devices; |
| `cc-link-ie` | `ispf-driver-cc-link-ie` | PRODUCTION | Apache-2.0 | MELSEC SLMP 3E binary (MC protocol) over TCP for D/R/W via CC-Link IE gateway; |
| `coap` | `ispf-driver-coap` | PRODUCTION | Apache-2.0 | CoAP GET клиент (только чтение) |
| `codesys` | `ispf-driver-codesys` | PRODUCTION | Apache-2.0 | CODESYS-lab text GET/SET gateway on TCP 1217 — not CODESYS network protocol / PLCHandler |
| `controlnet` | `ispf-driver-controlnet` | PRODUCTION | Apache-2.0 | ControlNet CIP Get_Attribute_Single over TCP gateway; not native ControlNet coax |
| `corba` | `ispf-driver-corba` | BETA | Apache-2.0 | CORBA IIOP TCP shell (без ORB в современном JDK) |
| `cwmp` | `ispf-driver-cwmp` | PRODUCTION | Apache-2.0 | TR-069/CWMP Inform + Get/SetParameterValues |
| `dali` | `ispf-driver-dali` | PRODUCTION | Apache-2.0 | IEC 62386 DALI forward frames (two bytes) over TCP; |
| `delta-dvp` | `ispf-driver-delta-dvp` | PRODUCTION | Apache-2.0 | Modbus TCP FC3/FC6 for Delta DVP/AS holding registers |
| `device-net` | `ispf-driver-device-net` | PRODUCTION | Apache-2.0 | DeviceNet CIP Get_Attribute_Single over TCP gateway; not DeviceNet CAN PHY |
| `dhcp` | `ispf-driver-dhcp` | PRODUCTION | Apache-2.0 | DHCP discover probe |
| `dlms` | `ispf-driver-dlms` | PRODUCTION | Apache-2.0 | DLMS/COSEM master счётчиков (TCP WRAPPER) |
| `dnp3` | `ispf-driver-dnp3` | PRODUCTION | Apache-2.0 | DNP3 TCP master — class poll, CROB latch и g41v3 analog direct operate |
| `eebus` | `ispf-driver-eebus` | PRODUCTION | Apache-2.0 | EEBus SPINE-lite TCP lab (ASCII GET/SET); |
| `email` | `ispf-driver-email` | PRODUCTION | Apache-2.0 | Исходящая почта через HTTP relay |
| `enocean` | `ispf-driver-enocean` | PRODUCTION | Apache-2.0 | EnOcean ESP3 serial-over-TCP (sync 0x55, CRC8 poly 0x07); |
| `ethercat` | `ispf-driver-ethercat` | PRODUCTION | Apache-2.0 | EtherCAT datagram over TCP (LRD/LWR logical access); |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | PRODUCTION | Apache-2.0 | EtherNet/IP CIP UCMM Read/Write Tag (класс Allen-Bradley) |
| `ethernet-powerlink` | `ispf-driver-ethernet-powerlink` | PRODUCTION | Apache-2.0 | POWERLINK frame over TCP gateway; not a hard MN |
| `fanuc-focas` | `ispf-driver-fanuc-focas` | PRODUCTION | Apache-2.0 | FOCAS-shaped TCP gateway lab — length-prefixed ASCII pmc/cnc/stat on 8193; |
| `fatek` | `ispf-driver-fatek` | PRODUCTION | Apache-2.0 | Fatek FACON ASCII command 46/47 register read-write over TCP |
| `file` | `ispf-driver-file` | PRODUCTION | Apache-2.0 | Опрос локального файла (метаданные/содержимое) |
| `flexible` | `ispf-driver-flexible` | PRODUCTION | Apache-2.0 | Гибкий TCP/UDP poller с настраиваемым framing |
| `folder` | `ispf-driver-folder` | PRODUCTION | Apache-2.0 | Опрос содержимого локальной директории |
| `foundation-fieldbus` | `ispf-driver-foundation-fieldbus` | PRODUCTION | Apache-2.0 | Foundation Fieldbus length-prefixed HSE-style probe over TCP; not an FDA session and not native H1 |
| `fuji-sph` | `ispf-driver-fuji-sph` | PRODUCTION | Apache-2.0 | Fuji SPH/MICREX computer-link ASCII lab (D/M/R) over TCP — not MICREX-SX |
| `ge-srtp` | `ispf-driver-ge-srtp` | PRODUCTION | Apache-2.0 | SRTP-lab MAILBOX read/write for %R/%AI/%AQ/%I/%Q over TCP — not full SRTP |
| `genicam` | `ispf-driver-genicam` | PRODUCTION | Apache-2.0 | GigE Vision GVCP discovery over UDP (control-protocol header starting 42 01 00 02); |
| `gps-tracker` | `ispf-driver-gps-tracker` | PRODUCTION | Apache-2.0 | TCP listener GPS/M2M трекеров |
| `graph-db` | `ispf-driver-graph-db` | PRODUCTION | Apache-2.0 | Запросы к графовой БД (Neo4j / Gremlin) |
| `graphql` | `ispf-driver-graphql` | PRODUCTION | Apache-2.0 | POSTs GraphQL queries (and optional mutations) over HTTP and maps data fields to points |
| `grpc` | `ispf-driver-grpc` | PRODUCTION | Apache-2.0 | lab HTTP/2 preface and empty SETTINGS; not HPACK and not a protobuf RPC |
| `hart-ip` | `ispf-driver-hart-ip` | PRODUCTION | Apache-2.0 | HART-IP over TCP: session initiate and pass-through short-frame PV read (commands 1 and 3); |
| `hart-serial` | `ispf-driver-hart-serial` | PRODUCTION | Apache-2.0 | HART short-frame over TCP serial gateway: preamble plus command 1/3 PV read; |
| `haystack` | `ispf-driver-haystack` | PRODUCTION | Apache-2.0 | Клиент Project Haystack HTTP JSON |
| `hitachi-hidic` | `ispf-driver-hitachi-hidic` | PRODUCTION | Apache-2.0 | Hitachi HIDIC/EH host-link ASCII lab (WR/R/M) over TCP — not verified HIDIC |
| `http` | `ispf-driver-http` | PRODUCTION | Apache-2.0 | HTTP/HTTPS клиент (GET/POST JSON/text) |
| `http-server` | `ispf-driver-http-server` | PRODUCTION | Apache-2.0 | Встроенный HTTP-сервер для входящих запросов |
| `icmp` | `ispf-driver-icmp` | PRODUCTION | Apache-2.0 | ICMP ping / проверка доступности и RTT |
| `idec-microsmart` | `ispf-driver-idec-microsmart` | PRODUCTION | Apache-2.0 | IDEC MicroSmart Host Link ASCII (D/M/R read-write) over TCP |
| `iec101` | `ispf-driver-iec101` | PRODUCTION | Apache-2.0 | IEC 60870-5-101 unbalanced FT1.2 over TCP: C_IC_NA_1, M_ME_NC_1, M_SP_NA_1, |
| `iec103` | `ispf-driver-iec103` | PRODUCTION | Apache-2.0 | IEC 60870-5-103 FT1.2 over TCP: ASDU 1/2/7/8/9/10/20/40; FUN/INF addressing; |
| `iec104` | `ispf-driver-iec104` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 клиент (телемеханика) |
| `iec104-server` | `ispf-driver-iec104-server` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 сервер/slave |
| `iec61850` | `ispf-driver-iec61850` | PRODUCTION | Apache-2.0 | IEC 61850 MMS client over TPKT/COTP (RFC 1006) on TCP 102; |
| `iec61850-goose` | `ispf-driver-iec61850-goose` | PRODUCTION | Apache-2.0 | IEC 61850-8-1 GOOSE header (APPID/Length/Reserved) over UDP; |
| `iec61850-sv` | `ispf-driver-iec61850-sv` | PRODUCTION | Apache-2.0 | IEC 61850-9-2 SAV header (APPID/Length/Reserved) over UDP; |
| `iec62056` | `ispf-driver-iec62056` | PRODUCTION | Apache-2.0 | IEC 62056-21 Mode C ASCII readout over TCP (sign-on /?! , identification, OBIS data lines); |
| `ieee2030-5` | `ispf-driver-ieee2030-5` | PRODUCTION | Apache-2.0 | IEEE 2030.5 (SEP2) HTTP/XML GET subset for EndDeviceList and MeterReading-style resources |
| `imap` | `ispf-driver-imap` | PRODUCTION | Apache-2.0 | Опрос IMAP почтового ящика |
| `ingress-sflow` | `ispf-driver-ingress-sflow` | PRODUCTION | Apache-2.0 | sFlow v5 UDP listener (сырой ingress) |
| `ingress-snmp-trap` | `ispf-driver-ingress-snmp-trap` | PRODUCTION | Apache-2.0 | SNMP trap UDP listener (сырой ingress) |
| `ingress-syslog` | `ispf-driver-ingress-syslog` | PRODUCTION | Apache-2.0 | Syslog UDP listener (сырой ingress) |
| `interbus` | `ispf-driver-interbus` | PRODUCTION | Apache-2.0 | INTERBUS process image over TCP; not the Phoenix ASIC |
| `io-link` | `ispf-driver-io-link` | PRODUCTION | Apache-2.0 | IO-Link ISDU over TCP master gateway; not the PHY |
| `ip-host` | `ispf-driver-ip-host` | PRODUCTION | Apache-2.0 | Мультипроверка хоста (PING/HTTP/TCP/DNS/SMTP/FTP) |
| `ipmi` | `ispf-driver-ipmi` | PRODUCTION | Apache-2.0 | IPMI LAN BMC probe |
| `isa100` | `ispf-driver-isa100` | PRODUCTION | Apache-2.0 | ISA100 gateway ASCII/JSON lab over TCP 4840; |
| `j1939` | `ispf-driver-j1939` | PRODUCTION | Apache-2.0 | SAE J1939 via LAWICEL SLCAN over TCP (extended T frames); |
| `jdbc` | `ispf-driver-jdbc` | PRODUCTION | Apache-2.0 | SQL JDBC SELECT poll |
| `jms` | `ispf-driver-jms` | PRODUCTION | Apache-2.0 | JMS клиент (класс ActiveMQ) |
| `jmx` | `ispf-driver-jmx` | PRODUCTION | Apache-2.0 | JMX poll атрибутов MBean (local/remote) |
| `kafka` | `ispf-driver-kafka` | PRODUCTION | Apache-2.0 | Apache Kafka consumer/poll |
| `keyence-hostlink` | `ispf-driver-keyence-hostlink` | PRODUCTION | Apache-2.0 | Reads/writes Keyence Host Link ASCII registers (RDS/WR) over TCP |
| `knx` | `ispf-driver-knx` | PRODUCTION | Apache-2.0 | KNXnet/IP Tunneling subset: search/connect + GroupValue Read/Write (cEMI) over UDP |
| `knx-tp` | `ispf-driver-knx-tp` | PRODUCTION | Apache-2.0 | KNXnet/IP SEARCH + Tunneling GroupValue Read/Write over UDP |
| `ldap` | `ispf-driver-ldap` | PRODUCTION | Apache-2.0 | LDAP search probe |
| `lonworks` | `ispf-driver-lonworks` | PRODUCTION | Apache-2.0 | LonWorks LonTalk-IP gateway lab (ASCII GET/SET); |
| `lorawan` | `ispf-driver-lorawan` | PRODUCTION | Apache-2.0 | Semtech UDP packet-forwarder network server: PUSH_DATA/PUSH_ACK and PULL_DATA/PULL_ACK |
| `ls-xgt` | `ispf-driver-ls-xgt` | PRODUCTION | Apache-2.0 | XGT dedicated read over TCP (not a certified FEnet stack) |
| `lwm2m` | `ispf-driver-lwm2m` | PRODUCTION | Apache-2.0 | OMA LwM2M resource read via CoAP GET (RFC 7252); |
| `matter` | `ispf-driver-matter` | PRODUCTION | Apache-2.0 | Matter TCP gateway lab (newline JSON attr/cmd on 5540); |
| `mbus` | `ispf-driver-mbus` | PRODUCTION | Apache-2.0 | M-Bus протокол счётчиков (read-only v0.1) |
| `message-stream` | `ispf-driver-message-stream` | PRODUCTION | Apache-2.0 | Универсальный TCP/UDP поток сообщений |
| `mitsubishi-melsec` | `ispf-driver-mitsubishi-melsec` | PRODUCTION | Apache-2.0 | Reads/writes MELSEC D registers via MC Protocol 3E binary (SLMP-compatible) over TCP |
| `mitsubishi-slmp` | `ispf-driver-mitsubishi-slmp` | PRODUCTION | Apache-2.0 | Reads/writes Mitsubishi PLC D registers via SLMP 3E binary over TCP |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | PRODUCTION | Apache-2.0 | Modbus RTU master по последовательному порту |
| `modbus-tcp` | `ispf-driver-modbus` | PRODUCTION | Apache-2.0 | Modbus TCP master (чтение/запись holding/input/coils) |
| `modbus-udp` | `ispf-driver-modbus-udp` | PRODUCTION | Apache-2.0 | Modbus UDP master |
| `modem-at` | `ispf-driver-modem-at` | PRODUCTION | Apache-2.0 | GSM/cellular AT-команды (TCP/serial) |
| `mqtt` | `ispf-driver-mqtt` | PRODUCTION | Apache-2.0 | MQTT 3.1.1 клиент (Paho mqttv3): подписка на топики и опциональная запись/publish |
| `mqtt-sn` | `ispf-driver-mqtt-sn` | PRODUCTION | Apache-2.0 | MQTT-SN 1.2 UDP client: CONNECT, REGISTER/SUBSCRIBE, PUBLISH QoS1 |
| `mtconnect` | `ispf-driver-mtconnect` | PRODUCTION | Apache-2.0 | HTTP/1.1 GET of MTConnect Agent /current or /sample XML; extracts data-item values |
| `nats` | `ispf-driver-nats` | PRODUCTION | Apache-2.0 | NATS text protocol client: INFO/CONNECT, SUB for reads, PUB for writes |
| `nmea` | `ispf-driver-nmea` | PRODUCTION | Apache-2.0 | Разбор NMEA 0183 (GNSS/датчики) |
| `ocpp` | `ispf-driver-ocpp` | PRODUCTION | Apache-2.0 | OCPP 1.6 JSON Charge Point subset (BootNotification/Heartbeat/StatusNotification) over WebSocket |
| `odata` | `ispf-driver-odata` | PRODUCTION | Apache-2.0 | Polls OData JSON v4 subset endpoints (value array / property) over HTTP GET |
| `odbc` | `ispf-driver-odbc` | PRODUCTION | Apache-2.0 | ODBC через внешний JDBC bridge JAR (SQL read) |
| `omron-fins` | `ispf-driver-omron-fins` | PRODUCTION | Apache-2.0 | Omron FINS ПЛК (read-only v0.1) |
| `onvif` | `ispf-driver-onvif` | PRODUCTION | Apache-2.0 | ONVIF Device SOAP subset: GetDeviceInformation, GetCapabilities, Get/SetHostname |
| `opc-ae` | `ispf-driver-opc-ae` | PRODUCTION | Apache-2.0 | HTTP/JSON A&E gateway lab — not OPC Classic DCOM A&E; |
| `opc-bridge` | `ispf-driver-opc-bridge` | BETA | Apache-2.0 | Оболочка OPC/LON TCP bridge |
| `opc-da` | `ispf-driver-opc-da` | BETA | Apache-2.0 | Оболочка OPC Classic DA (нужен Windows DCOM/bridge) |
| `opc-hda` | `ispf-driver-opc-hda` | PRODUCTION | Apache-2.0 | HDA gateway lab — not OPC Classic DCOM HDA; |
| `opcua` | `ispf-driver-opcua` | PRODUCTION | Apache-2.0 | OPC UA клиент (Eclipse Milo): poll/subscribe/write/browse |
| `opcua-pubsub` | `ispf-driver-opcua-pubsub` | PRODUCTION | Apache-2.0 | lab UADP version/publisher header; not a full OPC UA PubSub DataSetMessage or security stack |
| `opcua-server` | `ispf-driver-opcua-server` | PRODUCTION | Apache-2.0 | OPC UA сервер (Eclipse Milo), публикация переменных ISPF |
| `openadr` | `ispf-driver-openadr` | PRODUCTION | Apache-2.0 | OpenADR 2.0b oadrPoll VEN over HTTP/1.1 POST (not a full VTN or XML signature stack) |
| `panasonic-mewto` | `ispf-driver-panasonic-mewto` | PRODUCTION | Apache-2.0 | Reads/writes Panasonic MEWTOCOL-COM ASCII D/R registers over TCP |
| `plcnext` | `ispf-driver-plcnext` | PRODUCTION | Apache-2.0 | PLCnext RSC-lab HTTP/JSON symbol read/write on port 41100 |
| `pop3` | `ispf-driver-pop3` | PRODUCTION | Apache-2.0 | Опрос POP3 почтового ящика |
| `profibus` | `ispf-driver-profibus` | PRODUCTION | Apache-2.0 | PROFIBUS DP FDL over TCP (serial-server): SD1 station probe, SD2 slave/byte R/W; |
| `profibus-pa` | `ispf-driver-profibus-pa` | PRODUCTION | Apache-2.0 | PROFIBUS PA FDL over TCP (serial-server): SD1/SD2 for slot/addr/pa points; |
| `profinet` | `ispf-driver-profinet` | PRODUCTION | Apache-2.0 | PROFINET DCP Identify over TCP gateway; not PROFINET RT/IRT |
| `pulsar` | `ispf-driver-pulsar` | PRODUCTION | Apache-2.0 | lab binary header plus BaseCommand CONNECT; not a protobuf broker |
| `radius` | `ispf-driver-radius` | PRODUCTION | Apache-2.0 | Проверка RADIUS authentication |
| `redis` | `ispf-driver-redis` | PRODUCTION | Apache-2.0 | Polls Redis keys via RESP GET and writes via SET |
| `rockwell-csp` | `ispf-driver-rockwell-csp` | PRODUCTION | Apache-2.0 | CSP (PCCC-over-Ethernet) lab typed read/write (N/F/B) on TCP 2222 |
| `rockwell-df1` | `ispf-driver-rockwell-df1` | PRODUCTION | Apache-2.0 | DF1 full-duplex protected-mode typed read/write (N/F/B) over TCP serial bridge |
| `rtsp` | `ispf-driver-rtsp` | PRODUCTION | Apache-2.0 | RTSP/1.0 TCP DESCRIBE client with Session/Content-Base parsing |
| `s7` | `ispf-driver-s7` | PRODUCTION | Apache-2.0 | Siemens S7 ISO-on-TCP чтение/запись ПЛК |
| `schneider-umac` | `ispf-driver-schneider-umac` | PRODUCTION | Apache-2.0 | Modbus TCP FC3/FC6 for Modicon/Unity holding registers (not UMAS/Unity Pro proprietary) |
| `scpi` | `ispf-driver-scpi` | PRODUCTION | Apache-2.0 | Polls IEEE 488.2-style SCPI queries over TCP and writes ASCII set commands |
| `secs-gem` | `ispf-driver-secs-gem` | PRODUCTION | Apache-2.0 | SEMI E37 HSMS over TCP: Select.req/rsp, S1F1/S1F2, S1F13/S1F14, |
| `sigfox` | `ispf-driver-sigfox` | PRODUCTION | Apache-2.0 | Sigfox backend callback over plain HTTP/1.1 GET /uplink (no TLS; not Backend SDK) |
| `sip` | `ispf-driver-sip` | PRODUCTION | LicenseRef-NIST-PublicDomain | SIP OPTIONS/REGISTER probe доступности |
| `smb` | `ispf-driver-smb` | PRODUCTION | Apache-2.0 | Доступ к SMB/CIFS шарам |
| `smi-s` | `ispf-driver-smis` | PRODUCTION | Apache-2.0 | SMI-S storage CIM-XML poll |
| `smpp` | `ispf-driver-smpp` | PRODUCTION | Apache-2.0 | SMPP SMSC клиент |
| `sms` | `ispf-driver-sms` | PRODUCTION | Apache-2.0 | Исходящие SMS через HTTP relay |
| `snmp` | `ispf-driver-snmp` | PRODUCTION | Apache-2.0 | SNMP v1/v2c/v3 клиент GET/SET |
| `soap` | `ispf-driver-soap` | PRODUCTION | Apache-2.0 | SOAP HTTP клиент |
| `someip` | `ispf-driver-someip` | PRODUCTION | Apache-2.0 | AUTOSAR SOME/IP over TCP: 16-byte big-endian header, REQUEST/RESPONSE |
| `sparkplug-b` | `ispf-driver-sparkplug-b` | PRODUCTION | Apache-2.0 | Sparkplug B host: MQTT 3.1.1 subscribe NBIRTH/DBIRTH/DDATA, DCMD write, metric name mapping |
| `ssh` | `ispf-driver-ssh` | PRODUCTION | Apache-2.0 | Удалённое выполнение SSH (JSch) |
| `stomp` | `ispf-driver-stomp` | PRODUCTION | Apache-2.0 | STOMP 1.2 over TCP or WebSocket. A point is destination or destination\|JSON path |
| `telnet` | `ispf-driver-telnet` | PRODUCTION | Apache-2.0 | Telnet-сессия удалённых команд |
| `thread` | `ispf-driver-thread` | PRODUCTION | Apache-2.0 | OpenThread Spinel HDLC over TCP (CMD_RESET 7E 80 01 02 92 7E); |
| `toshiba-t-series` | `ispf-driver-toshiba-t-series` | PRODUCTION | Apache-2.0 | Toshiba T-series computer-link ASCII lab (D/X/Y) over TCP — not verified Toshiba computer-link |
| `uds` | `ispf-driver-uds` | PRODUCTION | Apache-2.0 | DoIP/UDS subset over TCP (0x10/0x22/0x2E) — not full ISO-TP / ISO 13400 stack |
| `unitronics` | `ispf-driver-unitronics` | PRODUCTION | Apache-2.0 | Unitronics PCOM ASCII (MI/MB read-write) over TCP |
| `virtual` | `ispf-driver-virtual` | PRODUCTION | Apache-2.0 | Симулятор / виртуальные профили устройств для демо и тестов |
| `visa` | `ispf-driver-visa` | PRODUCTION | Apache-2.0 | SOCKET-only VISA-style resource strings (TCPIP::host::port::SOCKET) for SCPI-over-TCP — not NI-VISA |
| `vmware` | `ispf-driver-vmware` | PRODUCTION | Apache-2.0 | VMware vSphere SOAP (Login + RetrieveProperties) |
| `wago` | `ispf-driver-wago` | PRODUCTION | Apache-2.0 | Modbus TCP FC3/FC6 for WAGO PFC holding registers (not CODESYS proprietary) |
| `weather-station` | `ispf-driver-weather-station` | PRODUCTION | Apache-2.0 | Davis Vantage LOOP command over TCP (LOOP\\n + ACK); not the full 99-byte Vantage packet; |
| `web-transaction` | `ispf-driver-web-transaction` | PRODUCTION | Apache-2.0 | Многошаговый HTTP transaction script |
| `webhook` | `ispf-driver-webhook` | PRODUCTION | Apache-2.0 | Исходящие webhook POST JSON уведомления |
| `websocket` | `ispf-driver-websocket` | PRODUCTION | Apache-2.0 | RFC6455 client. useTls encrypts the socket. Retries connect unless configuration is wrong |
| `weighbridge` | `ispf-driver-weighbridge` | PRODUCTION | Apache-2.0 | TCP MT-SICS level 0 (S/Z/T); not every truck-scale dialect |
| `wirelesshart` | `ispf-driver-wirelesshart` | PRODUCTION | Apache-2.0 | WirelessHART gateway via HART-IP TCP (8-byte header, pass-through); |
| `wisun` | `ispf-driver-wisun` | PRODUCTION | Apache-2.0 | CoAP RFC 7252 CON GET over UDP (port 5683); |
| `wmbus` | `ispf-driver-wmbus` | PRODUCTION | Apache-2.0 | Wireless M-Bus EN 13757-4 Format-A over TCP serial-server: CRC-16/EN-13757, |
| `wmi` | `ispf-driver-wmi` | PRODUCTION | Apache-2.0 | Windows WMI через PowerShell (только Windows) |
| `xmpp` | `ispf-driver-xmpp` | PRODUCTION | Apache-2.0 | XMPP клиент (Smack) |
| `yaskawa-memobus` | `ispf-driver-yaskawa-memobus` | PRODUCTION | Apache-2.0 | Reads/writes Yaskawa Memobus holding registers via Modbus TCP FC3/FC6 |
| `zigbee` | `ispf-driver-zigbee` | PRODUCTION | Apache-2.0 | ASH (EZSP UART) over TCP; |
| `zwave` | `ispf-driver-zwave` | PRODUCTION | Apache-2.0 | Z-Wave Serial API over TCP (SOF 0x01, ACK 0x06, XOR checksum); |

Подробные конфиги базовых драйверов — в разделах ниже. Остальные следуют тому же шаблону: `driverConfigJson` + `driverPointMappingsJson`, см. `DriverMetadata` в модуле.
