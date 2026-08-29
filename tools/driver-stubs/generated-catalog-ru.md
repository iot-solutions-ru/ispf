## Зарегистрированный каталог драйверов (162)

Поле `maturity` в `GET /api/v1/drivers`: `PRODUCTION` (по умолчанию), `BETA`, `STUB`. Метки задаются в `DriverMaturityRegistry` на сервере и отображаются в Web Console при выборе драйвера.

Поле `capabilities` — строковый набор из `DriverProductionMatrix` (ADR-0022): `read`, `write`, `subscribe`, `discovery`, `observed_at`, `quality`. Пример: `opcua` → `read`, `write`, `subscribe`, `discovery`, `observed_at`.

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
| `amqp` | `ispf-driver-amqp` | STUB | Apache-2.0 | AMQP: AMQP 0-9-1 / 1.0 broker stub (stub TCP-доступности; codec пока не реализован) |
| `ansi-c12` | `ispf-driver-ansi-c12` | STUB | Apache-2.0 | ANSI C12: ANSI C12.18/C12.22 meter stub (stub TCP-доступности; codec пока не реализован) |
| `application` | `ispf-driver-application` | PRODUCTION | Apache-2.0 | Локальные shell/скрипты → переменные ISPF |
| `as-interface` | `ispf-driver-as-interface` | STUB | Apache-2.0 | AS-Interface: AS-Interface master/gateway stub (stub TCP-доступности; codec пока не реализован) |
| `asterisk` | `ispf-driver-asterisk` | PRODUCTION | Apache-2.0 | Asterisk Manager Interface (AMI) |
| `aws-iot-core` | `ispf-driver-aws-iot-core` | STUB | Apache-2.0 | AWS IoT Core: AWS IoT Core MQTT/HTTP stub (stub TCP-доступности; codec пока не реализован) |
| `azure-iot-hub` | `ispf-driver-azure-iot-hub` | STUB | Apache-2.0 | Azure IoT Hub: Azure IoT Hub device/service stub (stub TCP-доступности; codec пока не реализован) |
| `bacnet` | `ispf-driver-bacnet` | PRODUCTION | Apache-2.0 | BACnet/IP клиент (clean-room codec) |
| `bacnet-mstp` | `ispf-driver-bacnet-mstp` | STUB | Apache-2.0 | BACnet MS/TP: BACnet MS/TP serial stub (BACnet/IP pack is separate) (stub TCP-доступности; codec пока не реализован) |
| `barcode-scanner` | `ispf-driver-barcode-scanner` | STUB | Apache-2.0 | Barcode scanner: Barcode/QR TCP/serial scanner stub (stub TCP-доступности; codec пока не реализован) |
| `beckhoff-ads` | `ispf-driver-beckhoff-ads` | STUB | Apache-2.0 | Beckhoff ADS: Beckhoff TwinCAT ADS/AMS stub (stub TCP-доступности; codec пока не реализован) |
| `bluetooth-le` | `ispf-driver-bluetooth-le` | STUB | Apache-2.0 | Bluetooth LE: Bluetooth Low Energy gateway stub (stub TCP-доступности; codec пока не реализован) |
| `camera-ai` | `ispf-driver-camera-ai` | STUB | Apache-2.0 | Camera AI edge: Edge vision/AI inference endpoint stub (stub TCP-доступности; codec пока не реализован) |
| `canbus-gateway` | `ispf-driver-canbus-gateway` | STUB | Apache-2.0 | CAN bus gateway: Generic CAN/CAN-FD TCP gateway stub (stub TCP-доступности; codec пока не реализован) |
| `canopen` | `ispf-driver-canopen` | STUB | Apache-2.0 | CANopen: CANopen / CAN gateway stub (stub TCP-доступности; codec пока не реализован) |
| `cc-link` | `ispf-driver-cc-link` | STUB | Apache-2.0 | CC-Link: Mitsubishi CC-Link field network stub (stub TCP-доступности; codec пока не реализован) |
| `cc-link-ie` | `ispf-driver-cc-link-ie` | STUB | Apache-2.0 | CC-Link IE: Mitsubishi CC-Link IE Field/Control stub (stub TCP-доступности; codec пока не реализован) |
| `coap` | `ispf-driver-coap` | PRODUCTION | Apache-2.0 | CoAP GET клиент (только чтение) |
| `codesys` | `ispf-driver-codesys` | STUB | Apache-2.0 | CODESYS Gateway: CODESYS gateway / PLCHandler stub (stub TCP-доступности; codec пока не реализован) |
| `controlnet` | `ispf-driver-controlnet` | STUB | Apache-2.0 | ControlNet: ODVA ControlNet gateway stub (stub TCP-доступности; codec пока не реализован) |
| `corba` | `ispf-driver-corba` | BETA | Apache-2.0 | CORBA IIOP TCP shell (без ORB в современном JDK) |
| `cwmp` | `ispf-driver-cwmp` | PRODUCTION | Apache-2.0 | TR-069/CWMP Inform + Get/SetParameterValues |
| `dali` | `ispf-driver-dali` | STUB | Apache-2.0 | DALI: DALI lighting gateway stub (stub TCP-доступности; codec пока не реализован) |
| `delta-dvp` | `ispf-driver-delta-dvp` | STUB | Apache-2.0 | Delta DVP: Delta DVP / AS series PLC stub (stub TCP-доступности; codec пока не реализован) |
| `device-net` | `ispf-driver-device-net` | STUB | Apache-2.0 | DeviceNet: ODVA DeviceNet gateway stub (stub TCP-доступности; codec пока не реализован) |
| `dhcp` | `ispf-driver-dhcp` | PRODUCTION | Apache-2.0 | DHCP discover probe |
| `dlms` | `ispf-driver-dlms` | PRODUCTION | Apache-2.0 | DLMS/COSEM master счётчиков (TCP WRAPPER) |
| `dnp3` | `ispf-driver-dnp3` | PRODUCTION | Apache-2.0 | DNP3 TCP master — class poll/read (запись не реализована) |
| `eebus` | `ispf-driver-eebus` | STUB | Apache-2.0 | EEBUS: EEBUS / SHIP energy management stub (stub TCP-доступности; codec пока не реализован) |
| `email` | `ispf-driver-email` | PRODUCTION | Apache-2.0 | Исходящая почта через HTTP relay |
| `enocean` | `ispf-driver-enocean` | STUB | Apache-2.0 | EnOcean: EnOcean ESP3 / USB gateway stub (stub TCP-доступности; codec пока не реализован) |
| `ethercat` | `ispf-driver-ethercat` | STUB | Apache-2.0 | EtherCAT: EtherCAT master/gateway stub (stub TCP-доступности; codec пока не реализован) |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | PRODUCTION | Apache-2.0 | EtherNet/IP CIP UCMM Read/Write Tag (класс Allen-Bradley) |
| `ethernet-powerlink` | `ispf-driver-ethernet-powerlink` | STUB | Apache-2.0 | Ethernet POWERLINK: Ethernet POWERLINK stub (stub TCP-доступности; codec пока не реализован) |
| `fanuc-focas` | `ispf-driver-fanuc-focas` | STUB | Apache-2.0 | Fanuc FOCAS: Fanuc FOCAS CNC stub (stub TCP-доступности; codec пока не реализован) |
| `fatek` | `ispf-driver-fatek` | STUB | Apache-2.0 | Fatek FACON: Fatek FACON protocol stub (stub TCP-доступности; codec пока не реализован) |
| `file` | `ispf-driver-file` | PRODUCTION | Apache-2.0 | Опрос локального файла (метаданные/содержимое) |
| `flexible` | `ispf-driver-flexible` | PRODUCTION | Apache-2.0 | Гибкий TCP/UDP poller с настраиваемым framing |
| `folder` | `ispf-driver-folder` | PRODUCTION | Apache-2.0 | Опрос содержимого локальной директории |
| `foundation-fieldbus` | `ispf-driver-foundation-fieldbus` | STUB | Apache-2.0 | Foundation Fieldbus: Foundation Fieldbus H1/HSE stub (stub TCP-доступности; codec пока не реализован) |
| `fuji-sph` | `ispf-driver-fuji-sph` | STUB | Apache-2.0 | Fuji SPH: Fuji Electric SPH / MICREX stub (stub TCP-доступности; codec пока не реализован) |
| `ge-srtp` | `ispf-driver-ge-srtp` | STUB | Apache-2.0 | GE SRTP: Emerson/GE Fanuc SRTP stub (stub TCP-доступности; codec пока не реализован) |
| `genicam` | `ispf-driver-genicam` | STUB | Apache-2.0 | GenICam: GenICam / GigE Vision stub (stub TCP-доступности; codec пока не реализован) |
| `gps-tracker` | `ispf-driver-gps-tracker` | PRODUCTION | Apache-2.0 | TCP listener GPS/M2M трекеров |
| `graph-db` | `ispf-driver-graph-db` | PRODUCTION | Apache-2.0 | Запросы к графовой БД (Neo4j / Gremlin) |
| `graphql` | `ispf-driver-graphql` | STUB | Apache-2.0 | GraphQL: GraphQL HTTP stub (stub TCP-доступности; codec пока не реализован) |
| `grpc` | `ispf-driver-grpc` | STUB | Apache-2.0 | gRPC: Generic gRPC telemetry stub (stub TCP-доступности; codec пока не реализован) |
| `hart-ip` | `ispf-driver-hart-ip` | STUB | Apache-2.0 | HART-IP: HART-IP (UDP/TCP) stub (stub TCP-доступности; codec пока не реализован) |
| `hart-serial` | `ispf-driver-hart-serial` | STUB | Apache-2.0 | HART serial: HART FSK serial/modem stub (stub TCP-доступности; codec пока не реализован) |
| `haystack` | `ispf-driver-haystack` | PRODUCTION | Apache-2.0 | Клиент Project Haystack HTTP JSON |
| `hitachi-hidic` | `ispf-driver-hitachi-hidic` | STUB | Apache-2.0 | Hitachi HIDIC: Hitachi HIDIC / EH-150 stub (stub TCP-доступности; codec пока не реализован) |
| `http` | `ispf-driver-http` | PRODUCTION | Apache-2.0 | HTTP/HTTPS клиент (GET/POST JSON/text) |
| `http-server` | `ispf-driver-http-server` | PRODUCTION | Apache-2.0 | Встроенный HTTP-сервер для входящих запросов |
| `icmp` | `ispf-driver-icmp` | PRODUCTION | Apache-2.0 | ICMP ping / проверка доступности и RTT |
| `idec-microsmart` | `ispf-driver-idec-microsmart` | STUB | Apache-2.0 | IDEC MicroSmart: IDEC MicroSmart FC6A stub (stub TCP-доступности; codec пока не реализован) |
| `iec101` | `ispf-driver-iec101` | STUB | Apache-2.0 | IEC 60870-5-101: IEC 60870-5-101 serial/TCP stub (stub TCP-доступности; codec пока не реализован) |
| `iec103` | `ispf-driver-iec103` | STUB | Apache-2.0 | IEC 60870-5-103: IEC 60870-5-103 protection stub (stub TCP-доступности; codec пока не реализован) |
| `iec104` | `ispf-driver-iec104` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 клиент (телемеханика) |
| `iec104-server` | `ispf-driver-iec104-server` | PRODUCTION | Apache-2.0 | IEC 60870-5-104 сервер/slave |
| `iec61850` | `ispf-driver-iec61850` | STUB | Apache-2.0 | IEC 61850 MMS: IEC 61850 MMS client stub (stub TCP-доступности; codec пока не реализован) |
| `iec61850-goose` | `ispf-driver-iec61850-goose` | STUB | Apache-2.0 | IEC 61850 GOOSE: IEC 61850 GOOSE subscriber stub (stub TCP-доступности; codec пока не реализован) |
| `iec61850-sv` | `ispf-driver-iec61850-sv` | STUB | Apache-2.0 | IEC 61850 Sampled Values: IEC 61850 Sampled Values stub (stub TCP-доступности; codec пока не реализован) |
| `iec62056` | `ispf-driver-iec62056` | STUB | Apache-2.0 | IEC 62056: IEC 62056 DLMS companion / push stub (beyond existing DLMS pack) (stub TCP-доступности; codec пока не реализован) |
| `ieee2030-5` | `ispf-driver-ieee2030-5` | STUB | Apache-2.0 | IEEE 2030.5: IEEE 2030.5 (SEP2) stub (stub TCP-доступности; codec пока не реализован) |
| `imap` | `ispf-driver-imap` | PRODUCTION | Apache-2.0 | Опрос IMAP почтового ящика |
| `ingress-sflow` | `ispf-driver-ingress-sflow` | PRODUCTION | Apache-2.0 | sFlow v5 UDP listener (сырой ingress) |
| `ingress-snmp-trap` | `ispf-driver-ingress-snmp-trap` | PRODUCTION | Apache-2.0 | SNMP trap UDP listener (сырой ingress) |
| `ingress-syslog` | `ispf-driver-ingress-syslog` | PRODUCTION | Apache-2.0 | Syslog UDP listener (сырой ingress) |
| `interbus` | `ispf-driver-interbus` | STUB | Apache-2.0 | INTERBUS: INTERBUS fieldbus gateway stub (stub TCP-доступности; codec пока не реализован) |
| `io-link` | `ispf-driver-io-link` | STUB | Apache-2.0 | IO-Link: IO-Link master REST/MQTT bridge stub (stub TCP-доступности; codec пока не реализован) |
| `ip-host` | `ispf-driver-ip-host` | PRODUCTION | Apache-2.0 | Мультипроверка хоста (PING/HTTP/TCP/DNS/SMTP/FTP) |
| `ipmi` | `ispf-driver-ipmi` | PRODUCTION | Apache-2.0 | IPMI LAN BMC probe |
| `isa100` | `ispf-driver-isa100` | STUB | Apache-2.0 | ISA100.11a: ISA100 wireless gateway stub (stub TCP-доступности; codec пока не реализован) |
| `j1939` | `ispf-driver-j1939` | STUB | Apache-2.0 | SAE J1939: SAE J1939 vehicle network stub (stub TCP-доступности; codec пока не реализован) |
| `jdbc` | `ispf-driver-jdbc` | PRODUCTION | Apache-2.0 | SQL JDBC SELECT poll |
| `jms` | `ispf-driver-jms` | PRODUCTION | Apache-2.0 | JMS клиент (класс ActiveMQ) |
| `jmx` | `ispf-driver-jmx` | PRODUCTION | Apache-2.0 | JMX poll атрибутов MBean (local/remote) |
| `kafka` | `ispf-driver-kafka` | PRODUCTION | Apache-2.0 | Apache Kafka consumer/poll |
| `keyence-hostlink` | `ispf-driver-keyence-hostlink` | STUB | Apache-2.0 | Keyence Host Link: Keyence PLC Host Link / KV stub (stub TCP-доступности; codec пока не реализован) |
| `knx` | `ispf-driver-knx` | STUB | Apache-2.0 | KNX/IP: KNX/IP tunneling/routing stub (stub TCP-доступности; codec пока не реализован) |
| `knx-tp` | `ispf-driver-knx-tp` | STUB | Apache-2.0 | KNX TP: KNX Twisted Pair interface stub (stub TCP-доступности; codec пока не реализован) |
| `ldap` | `ispf-driver-ldap` | PRODUCTION | Apache-2.0 | LDAP search probe |
| `lonworks` | `ispf-driver-lonworks` | STUB | Apache-2.0 | LonWorks: LonWorks/LonTalk IP stub (stub TCP-доступности; codec пока не реализован) |
| `lorawan` | `ispf-driver-lorawan` | STUB | Apache-2.0 | LoRaWAN: LoRaWAN network/application server gateway stub (stub TCP-доступности; codec пока не реализован) |
| `ls-xgt` | `ispf-driver-ls-xgt` | STUB | Apache-2.0 | LS XGT: LS Electric XGT FEnet stub (stub TCP-доступности; codec пока не реализован) |
| `lwm2m` | `ispf-driver-lwm2m` | STUB | Apache-2.0 | LwM2M: OMA LwM2M client/server stub (stub TCP-доступности; codec пока не реализован) |
| `matter` | `ispf-driver-matter` | STUB | Apache-2.0 | Matter: Matter / CHIP controller stub (stub TCP-доступности; codec пока не реализован) |
| `mbus` | `ispf-driver-mbus` | PRODUCTION | Apache-2.0 | M-Bus протокол счётчиков (read-only v0.1) |
| `message-stream` | `ispf-driver-message-stream` | PRODUCTION | Apache-2.0 | Универсальный TCP/UDP поток сообщений |
| `mitsubishi-melsec` | `ispf-driver-mitsubishi-melsec` | STUB | Apache-2.0 | Mitsubishi MELSEC: Mitsubishi MELSEC communication stub (MC Protocol / SLMP path planned) (stub TCP-доступности; codec пока не реализован) |
| `mitsubishi-slmp` | `ispf-driver-mitsubishi-slmp` | STUB | Apache-2.0 | Mitsubishi SLMP: Mitsubishi SLMP (Seamless Message Protocol) stub (stub TCP-доступности; codec пока не реализован) |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | PRODUCTION | Apache-2.0 | Modbus RTU master по последовательному порту |
| `modbus-tcp` | `ispf-driver-modbus` | PRODUCTION | Apache-2.0 | Modbus TCP master (чтение/запись holding/input/coils) |
| `modbus-udp` | `ispf-driver-modbus-udp` | PRODUCTION | Apache-2.0 | Modbus UDP master |
| `modem-at` | `ispf-driver-modem-at` | PRODUCTION | Apache-2.0 | GSM/cellular AT-команды (TCP/serial) |
| `mqtt` | `ispf-driver-mqtt` | PRODUCTION | Apache-2.0 | MQTT-клиент: подписка на топики и опциональная запись/publish |
| `mqtt-sn` | `ispf-driver-mqtt-sn` | STUB | Apache-2.0 | MQTT-SN: MQTT For Sensor Networks stub (stub TCP-доступности; codec пока не реализован) |
| `mtconnect` | `ispf-driver-mtconnect` | STUB | Apache-2.0 | MTConnect: MTConnect agent HTTP stub (stub TCP-доступности; codec пока не реализован) |
| `nats` | `ispf-driver-nats` | STUB | Apache-2.0 | NATS: NATS messaging stub (cluster messaging is separate) (stub TCP-доступности; codec пока не реализован) |
| `nmea` | `ispf-driver-nmea` | PRODUCTION | Apache-2.0 | Разбор NMEA 0183 (GNSS/датчики) |
| `ocpp` | `ispf-driver-ocpp` | STUB | Apache-2.0 | OCPP: Open Charge Point Protocol (CSMS) stub (stub TCP-доступности; codec пока не реализован) |
| `odata` | `ispf-driver-odata` | STUB | Apache-2.0 | OData: OData v4 REST stub (stub TCP-доступности; codec пока не реализован) |
| `odbc` | `ispf-driver-odbc` | PRODUCTION | Apache-2.0 | ODBC через внешний JDBC bridge JAR (SQL read) |
| `omron-fins` | `ispf-driver-omron-fins` | PRODUCTION | Apache-2.0 | Omron FINS ПЛК (read-only v0.1) |
| `onvif` | `ispf-driver-onvif` | STUB | Apache-2.0 | ONVIF: ONVIF Profile S/T device stub (stub TCP-доступности; codec пока не реализован) |
| `opc-ae` | `ispf-driver-opc-ae` | STUB | Apache-2.0 | OPC Alarms and Events: OPC Classic A&E stub (DCOM/bridge required) (stub TCP-доступности; codec пока не реализован) |
| `opc-bridge` | `ispf-driver-opc-bridge` | BETA | Apache-2.0 | Оболочка OPC/LON TCP bridge |
| `opc-da` | `ispf-driver-opc-da` | BETA | Apache-2.0 | Оболочка OPC Classic DA (нужен Windows DCOM/bridge) |
| `opc-hda` | `ispf-driver-opc-hda` | STUB | Apache-2.0 | OPC Historical Data Access: OPC Classic HDA stub (DCOM/bridge required) (stub TCP-доступности; codec пока не реализован) |
| `opcua` | `ispf-driver-opcua` | PRODUCTION | Apache-2.0 | OPC UA клиент (Eclipse Milo): poll/subscribe/write/browse |
| `opcua-pubsub` | `ispf-driver-opcua-pubsub` | STUB | Apache-2.0 | OPC UA PubSub: OPC UA PubSub (UDP/MQTT) stub — connectivity shell only (stub TCP-доступности; codec пока не реализован) |
| `opcua-server` | `ispf-driver-opcua-server` | PRODUCTION | Apache-2.0 | OPC UA сервер (Eclipse Milo), публикация переменных ISPF |
| `openadr` | `ispf-driver-openadr` | STUB | Apache-2.0 | OpenADR: OpenADR 2.0b VTN/VEN stub (stub TCP-доступности; codec пока не реализован) |
| `panasonic-mewto` | `ispf-driver-panasonic-mewto` | STUB | Apache-2.0 | Panasonic MEWTOCOL: Panasonic MEWTOCOL-COM/DAT stub (stub TCP-доступности; codec пока не реализован) |
| `plcnext` | `ispf-driver-plcnext` | STUB | Apache-2.0 | PLCnext: Phoenix Contact PLCnext Engineer/RSC stub (stub TCP-доступности; codec пока не реализован) |
| `pop3` | `ispf-driver-pop3` | PRODUCTION | Apache-2.0 | Опрос POP3 почтового ящика |
| `profibus` | `ispf-driver-profibus` | STUB | Apache-2.0 | PROFIBUS: PROFIBUS DP/PA gateway stub (serial/fieldbus bridge required) (stub TCP-доступности; codec пока не реализован) |
| `profibus-pa` | `ispf-driver-profibus-pa` | STUB | Apache-2.0 | PROFIBUS PA: PROFIBUS PA instrument network stub (stub TCP-доступности; codec пока не реализован) |
| `profinet` | `ispf-driver-profinet` | STUB | Apache-2.0 | PROFINET IO: PROFINET IO controller/device stub (DCP/RPC not implemented) (stub TCP-доступности; codec пока не реализован) |
| `pulsar` | `ispf-driver-pulsar` | STUB | Apache-2.0 | Apache Pulsar: Apache Pulsar client stub (stub TCP-доступности; codec пока не реализован) |
| `radius` | `ispf-driver-radius` | PRODUCTION | Apache-2.0 | Проверка RADIUS authentication |
| `redis` | `ispf-driver-redis` | STUB | Apache-2.0 | Redis: Redis key/stream telemetry stub (stub TCP-доступности; codec пока не реализован) |
| `rockwell-csp` | `ispf-driver-rockwell-csp` | STUB | Apache-2.0 | Rockwell CSP: Allen-Bradley CSP (legacy Ethernet) stub (stub TCP-доступности; codec пока не реализован) |
| `rockwell-df1` | `ispf-driver-rockwell-df1` | STUB | Apache-2.0 | Rockwell DF1: Allen-Bradley DF1 serial/TCP bridge stub (stub TCP-доступности; codec пока не реализован) |
| `rtsp` | `ispf-driver-rtsp` | STUB | Apache-2.0 | RTSP: RTSP media/metadata stub (stub TCP-доступности; codec пока не реализован) |
| `s7` | `ispf-driver-s7` | PRODUCTION | Apache-2.0 | Siemens S7 ISO-on-TCP чтение/запись ПЛК |
| `schneider-umac` | `ispf-driver-schneider-umac` | STUB | Apache-2.0 | Schneider Unity/Modicon: Schneider Electric Unity/Modicon advanced services stub (beyond Modbus) (stub TCP-доступности; codec пока не реализован) |
| `scpi` | `ispf-driver-scpi` | STUB | Apache-2.0 | SCPI: IEEE 488.2 SCPI instrument stub (stub TCP-доступности; codec пока не реализован) |
| `secs-gem` | `ispf-driver-secs-gem` | STUB | Apache-2.0 | SECS/GEM: SEMI SECS-I/HSMS/GEM stub (stub TCP-доступности; codec пока не реализован) |
| `sigfox` | `ispf-driver-sigfox` | STUB | Apache-2.0 | Sigfox: Sigfox backend callback stub (stub TCP-доступности; codec пока не реализован) |
| `sip` | `ispf-driver-sip` | PRODUCTION | LicenseRef-NIST-PublicDomain | SIP OPTIONS/REGISTER probe доступности |
| `smb` | `ispf-driver-smb` | PRODUCTION | Apache-2.0 | Доступ к SMB/CIFS шарам |
| `smi-s` | `ispf-driver-smis` | PRODUCTION | Apache-2.0 | SMI-S storage CIM-XML poll |
| `smpp` | `ispf-driver-smpp` | PRODUCTION | Apache-2.0 | SMPP SMSC клиент |
| `sms` | `ispf-driver-sms` | PRODUCTION | Apache-2.0 | Исходящие SMS через HTTP relay |
| `snmp` | `ispf-driver-snmp` | PRODUCTION | Apache-2.0 | SNMP v1/v2c/v3 клиент GET/SET |
| `soap` | `ispf-driver-soap` | PRODUCTION | Apache-2.0 | SOAP HTTP клиент |
| `someip` | `ispf-driver-someip` | STUB | Apache-2.0 | SOME/IP: AUTOSAR SOME/IP stub (stub TCP-доступности; codec пока не реализован) |
| `sparkplug-b` | `ispf-driver-sparkplug-b` | STUB | Apache-2.0 | MQTT Sparkplug B: MQTT Sparkplug B host/edge stub (MQTT session + Sparkplug payload parsing not implemented) (stub TCP-доступности; codec пока не реализован) |
| `ssh` | `ispf-driver-ssh` | PRODUCTION | Apache-2.0 | Удалённое выполнение SSH (JSch) |
| `telnet` | `ispf-driver-telnet` | PRODUCTION | Apache-2.0 | Telnet-сессия удалённых команд |
| `thread` | `ispf-driver-thread` | STUB | Apache-2.0 | Thread: Thread Border Router stub (stub TCP-доступности; codec пока не реализован) |
| `toshiba-t-series` | `ispf-driver-toshiba-t-series` | STUB | Apache-2.0 | Toshiba T-series: Toshiba T-series PLC stub (stub TCP-доступности; codec пока не реализован) |
| `uds` | `ispf-driver-uds` | STUB | Apache-2.0 | UDS (ISO 14229): Unified Diagnostic Services over DoIP stub (stub TCP-доступности; codec пока не реализован) |
| `unitronics` | `ispf-driver-unitronics` | STUB | Apache-2.0 | Unitronics: Unitronics PCOM stub (stub TCP-доступности; codec пока не реализован) |
| `virtual` | `ispf-driver-virtual` | PRODUCTION | Apache-2.0 | Симулятор / виртуальные профили устройств для демо и тестов |
| `visa` | `ispf-driver-visa` | STUB | Apache-2.0 | VISA: IVI/VISA instrument resource stub (stub TCP-доступности; codec пока не реализован) |
| `vmware` | `ispf-driver-vmware` | PRODUCTION | Apache-2.0 | VMware vSphere SOAP (Login + RetrieveProperties) |
| `wago` | `ispf-driver-wago` | STUB | Apache-2.0 | WAGO: WAGO PFC / e!COCKPIT stub (stub TCP-доступности; codec пока не реализован) |
| `weather-station` | `ispf-driver-weather-station` | STUB | Apache-2.0 | Weather station: Davis/Vaisala-class weather station stub (stub TCP-доступности; codec пока не реализован) |
| `web-transaction` | `ispf-driver-web-transaction` | PRODUCTION | Apache-2.0 | Многошаговый HTTP transaction script |
| `webhook` | `ispf-driver-webhook` | PRODUCTION | Apache-2.0 | Исходящие webhook POST JSON уведомления |
| `websocket` | `ispf-driver-websocket` | STUB | Apache-2.0 | WebSocket: Generic WebSocket telemetry stub (stub TCP-доступности; codec пока не реализован) |
| `weighbridge` | `ispf-driver-weighbridge` | STUB | Apache-2.0 | Weighbridge: Truck scale / weighbridge protocol stub (stub TCP-доступности; codec пока не реализован) |
| `wirelesshart` | `ispf-driver-wirelesshart` | STUB | Apache-2.0 | WirelessHART: WirelessHART gateway stub (stub TCP-доступности; codec пока не реализован) |
| `wisun` | `ispf-driver-wisun` | STUB | Apache-2.0 | Wi-SUN: Wi-SUN FAN border router stub (stub TCP-доступности; codec пока не реализован) |
| `wmbus` | `ispf-driver-wmbus` | STUB | Apache-2.0 | Wireless M-Bus: Wireless M-Bus (OMS) stub (stub TCP-доступности; codec пока не реализован) |
| `wmi` | `ispf-driver-wmi` | PRODUCTION | Apache-2.0 | Windows WMI через PowerShell (только Windows) |
| `xmpp` | `ispf-driver-xmpp` | PRODUCTION | Apache-2.0 | XMPP клиент (Smack) |
| `yaskawa-memobus` | `ispf-driver-yaskawa-memobus` | STUB | Apache-2.0 | Yaskawa Memobus: Yaskawa Memobus/Modbus-family PLC stub (stub TCP-доступности; codec пока не реализован) |
| `zigbee` | `ispf-driver-zigbee` | STUB | Apache-2.0 | Zigbee: Zigbee coordinator / ZCL stub (stub TCP-доступности; codec пока не реализован) |
| `zwave` | `ispf-driver-zwave` | STUB | Apache-2.0 | Z-Wave: Z-Wave controller stub (stub TCP-доступности; codec пока не реализован) |

Подробные конфиги базовых драйверов — в разделах ниже. Остальные следуют тому же шаблону: `driverConfigJson` + `driverPointMappingsJson`, см. `DriverMetadata` в модуле.
