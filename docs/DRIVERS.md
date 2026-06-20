# Драйверы устройств

Каталог кандидатов на новые драйверы (roadmap): [PLATFORM_DEVELOPER_BACKLOG.md §10](PLATFORM_DEVELOPER_BACKLOG.md#10-каталог-драйверов-устройств-roadmap) (REQ-PF-14).

## Архитектура

Драйверы реализуют SPI `DeviceDriver` (`packages/ispf-driver-api`):

```java
public interface DeviceDriver {
    DriverMetadata metadata();
    void connect(Map<String, String> config) throws DriverException;
    void disconnect();
    void readPoints() throws DriverException;
    void writePoint(String pointId, DataRecord value) throws DriverException;
}
```

Регистрация в `DriverCatalog` (server). Runtime — `DriverRuntimeService`: poll loop по `pollIntervalMs`.

## Переменные устройства (driver group)

На объекте `DEVICE` с моделью задаются:

| Переменная | Описание |
|------------|----------|
| `driverId` | ID драйвера — полный список см. таблицу ниже |
| `driverStatus` | `STOPPED` / `RUNNING` / `ERROR` |
| `driverPollIntervalMs` | Интервал опроса |
| `driverConfigJson` | JSON конфигурации |
| `driverPointMappingsJson` | JSON: `variableName → pointId` |

## REST Runtime API

```http
POST /api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01
POST /api/v1/drivers/runtime/stop?devicePath=...
PUT  /api/v1/drivers/runtime/configure?devicePath=...
GET  /api/v1/drivers/runtime/status?devicePath=...
```

## Встроенные драйверы

### virtual (`ispf-driver-virtual`)

Симулятор для стенда без железа. Профиль задаётся в `driverConfigJson.profile`:

| `profile` | Переменные | Назначение |
|-----------|------------|------------|
| `demo` (default) | `temperature`, `status` | Синусоида температуры |
| `meter` | `meterLiters`, `flowRate`, `filling` | Налив: `litersPerSecond`, `filling` |
| `weighbridge` | `grossWeight`, `tareKg` | `tareKg + meterLiters * density` |
| `rack-signals` | `gasPresent`, `groundConnected` | Булевы сигналы по `rackId` |

Пример meter (virtual driver profile):

```json
{
  "profile": "meter",
  "litersPerSecond": "120",
  "filling": "true"
}
```

Конфиг demo-температуры:

```json
{
  "profile": "demo",
  "baseTemperature": "22.0",
  "amplitude": "15.0",
  "periodSec": "60"
}
```

### mqtt (`ispf-driver-mqtt`)

Eclipse Paho, подписка на топики.

Конфиг: `brokerUrl`, `topicPrefix`, `clientId`, credentials.

Point mapping: `variableName → mqttTopicSuffix`.

### modbus-tcp (`ispf-driver-modbus`)

j2mod, Modbus TCP.

Формат точки: `slaveId:registerType:address[:count]`

Типы регистров: `HOLDING`, `INPUT`, `COIL`, `DISCRETE`.

### snmp (`ispf-driver-snmp`)

SNMP4J, v1/v2c/v3 GET/SET (v3: USM MD5/SHA + DES/AES128).

Формат точки: `oid` или `oid:VALUE_KIND` (`STRING`, `INTEGER`, …).

Демо `snmp-localhost`: MIB-II OIDs (`sysName`, `sysDescr`, `sysUpTime`, `sysLocation`).

Конфиг v2c:

```json
{
  "host": "127.0.0.1",
  "port": "161",
  "community": "public",
  "version": "2c",
  "timeoutMs": "3000",
  "retries": "1"
}
```

Конфиг v3 (дополнительно):

```json
{
  "version": "3",
  "securityName": "snmpuser",
  "authProtocol": "MD5",
  "authPassphrase": "authpass",
  "privProtocol": "DES",
  "privPassphrase": "privpass"
}
```

### http (`ispf-driver-http`)

HTTP/HTTPS client (Java HttpClient). Опрос REST endpoints.

Point mapping: `path`, `GET:path`, `HEAD:path`, полный URL, суффикс `:json` для строкового JSON-скаляра.

```json
{
  "baseUrl": "http://127.0.0.1:8080",
  "timeoutMs": "5000"
}
```

Пример mappings: `{"platformVersion": "GET:/api/v1/info:json"}`

### icmp (`ispf-driver-icmp`)

Доступность хоста (ICMP / `InetAddress.isReachable`).

Point mapping: hostname или IP на переменную; пустое значение — `host` из конфига.

```json
{
  "host": "127.0.0.1",
  "timeoutMs": "3000"
}
```

Переменная получает: `reachable`, `latencyMs`, `host`.

### ssh (`ispf-driver-ssh`)

Удалённое выполнение shell-команды (JSch).

Point mapping: команда на переменную, например `uptime`.

```json
{
  "host": "192.168.1.10",
  "port": "22",
  "username": "admin",
  "password": "secret",
  "timeoutMs": "10000"
}
```

Переменная: `value` (stdout), `exitCode`, `stderr`.

### coap (`ispf-driver-coap`)

CoAP client (Eclipse Californium), GET ресурсов IoT-устройств.

Point mapping: путь `/sensor/temp` или полный `coap://host:5683/...`

```json
{
  "host": "127.0.0.1",
  "port": "5683",
  "timeoutMs": "5000"
}
```

## Каталог зарегистрированных драйверов (31)

| `driverId` | Модуль | Назначение |
|------------|--------|------------|
| `virtual` | `ispf-driver-virtual` | Симулятор (профили demo/meter/…) |
| `mqtt` | `ispf-driver-mqtt` | MQTT subscribe |
| `modbus-tcp` | `ispf-driver-modbus` | Modbus TCP (j2mod) |
| `snmp` | `ispf-driver-snmp` | SNMP v1/v2c/v3 |
| `http` | `ispf-driver-http` | HTTP/HTTPS client |
| `icmp` | `ispf-driver-icmp` | Ping / reachability |
| `ssh` | `ispf-driver-ssh` | SSH remote command |
| `coap` | `ispf-driver-coap` | CoAP GET |
| `opcua` | `ispf-driver-opcua` | OPC UA client (Eclipse Milo) |
| `s7` | `ispf-driver-s7` | Siemens S7 (s7connector) |
| `iec104` | `ispf-driver-iec104` | IEC 60870-5-104 client (j60870) |
| `bacnet` | `ispf-driver-bacnet` | BACnet/IP (bacnet4j) |
| `dnp3` | `ispf-driver-dnp3` | DNP3 TCP (connectivity; полный стек — native) |
| `jmx` | `ispf-driver-jmx` | JMX MBean attributes |
| `jdbc` | `ispf-driver-jdbc` | SQL query scalar |
| `file` | `ispf-driver-file` | Чтение/метаданные файла |
| `folder` | `ispf-driver-folder` | Содержимое каталога |
| `application` | `ispf-driver-application` | Запуск shell/script |
| `message-stream` | `ispf-driver-message-stream` | TCP/UDP поток сообщений |
| `nmea` | `ispf-driver-nmea` | NMEA 0183 (serial/TCP) |
| `telnet` | `ispf-driver-telnet` | Telnet command |
| `soap` | `ispf-driver-soap` | SOAP HTTP call |
| `ip-host` | `ispf-driver-ip-host` | IT checks: PING, HTTP, TCP, DNS, SMTP, FTP |
| `kafka` | `ispf-driver-kafka` | Kafka consume/produce |
| `gps-tracker` | `ispf-driver-gps-tracker` | TCP server — входящие M2M/GPS |
| `flexible` | `ispf-driver-flexible` | Настраиваемый TCP/UDP request/response |
| `mbus` | `ispf-driver-mbus` | M-Bus (jmbus) |
| `omron-fins` | `ispf-driver-omron-fins` | Omron FINS/TCP |
| `asterisk` | `ispf-driver-asterisk` | Asterisk AMI |
| `smpp` | `ispf-driver-smpp` | SMPP bind/send |
| `smb` | `ispf-driver-smb` | SMB share file check (smbj) |

### opcua (`ispf-driver-opcua`)

OPC UA client. Конфиг: `endpointUrl`, `securityPolicy` (optional), credentials.

Point mapping: `ns=2;s=TagName` (NodeId).

### s7 (`ispf-driver-s7`)

Siemens S7 over ISO-on-TCP. Конфиг: `host`, `port` (102), `rack`, `slot`, `timeoutMs`.

Point mapping: `DB1.DBD0:REAL` или area/offset.

### iec104 (`ispf-driver-iec104`)

IEC 60870-5-104 master. Конфиг: `host`, `port` (2404), `commonAddress`.

Point mapping: `ioa:type` (например `1001:M_ME_NC_1`).

### bacnet (`ispf-driver-bacnet`)

BACnet/IP read property. Конфиг: `broadcastAddress`, `localBindAddress`, `port` (47808).

Point mapping: `deviceId:objectType:instance:propertyId`.

### dnp3 (`ispf-driver-dnp3`)

TCP-сессия к outstation; полный DNP3 application layer требует native (`io.stepfunc:dnp3`).

Point mapping: `index:dataType` (например `0:ANALOG_INPUT`). Статус: `connected`, `reachable`.

### jmx (`ispf-driver-jmx`)

JMX remote или local. Конфиг: `serviceUrl` (`service:jmx:rmi:///jndi/rmi://host:port/jmxrmi`) или пусто для local.

Point mapping: `ObjectName:attribute` (например `java.lang:type=Memory:HeapMemoryUsage`).

### jdbc (`ispf-driver-jdbc`)

SQL scalar. Конфиг: `jdbcUrl`, `username`, `password`, `query`, `timeoutMs`.

Point mapping: имя колонки или `value` для одной ячейки.

### file / folder

`file`: путь → `exists`, `size`, `lastModified`, `content` (текст).  
`folder`: путь → список файлов, счётчики.

### application (`ispf-driver-application`)

Запуск процесса. Конфиг: `command`, `workingDir`, `timeoutMs`.

Point mapping: переменная → аргумент или `stdout`/`exitCode`.

### message-stream (`ispf-driver-message-stream`)

TCP client/server или UDP. Конфиг: `mode`, `host`, `port`, `encoding`.

Point mapping: `feed` — последнее сообщение.

### nmea (`ispf-driver-nmea`)

NMEA 0183. Конфиг: `mode` (tcp/serial), `host`/`port` или `serialPort`.

Point mapping: sentence type (`GGA`, `RMC`) или `raw`.

### telnet / soap

`telnet`: host, port, credentials; mapping — команда.  
`soap`: `endpointUrl`, `soapAction`; mapping — тело запроса или XPath-подобный путь к значению.

### ip-host (`ispf-driver-ip-host`)

Единый IT-мониторинг. Point mapping префиксы:

| Префикс | Пример | Проверка |
|---------|--------|----------|
| `PING:` | `PING:8.8.8.8` | ICMP |
| `HTTP:` | `HTTP:https://host/` | HTTP HEAD |
| `TCP:` | `TCP:host:443` | TCP connect |
| `DNS:` | `DNS:example.com` | DNS resolve |
| `SMTP:` | `SMTP:host:25` | SMTP banner |
| `FTP:` | `FTP:host:21` | FTP connect |

### kafka (`ispf-driver-kafka`)

Конфиг: `bootstrapServers`, `topic`, `groupId`, `timeoutMs`.

Point mapping: `consume` (последнее сообщение) или `produce:payload`.

### gps-tracker (`ispf-driver-gps-tracker`)

TCP **server** — устройства подключаются к платформе. Конфиг: `listenPort`, `bufferSize`.

Point mapping: `feed` — последняя строка/буфер.

### flexible (`ispf-driver-flexible`)

Универсальный TCP/UDP. Конфиг: `protocol`, `host`, `port`, `encoding` (`hex`|`utf8`), `timeoutMs`.

Point mapping: `request[:responseRegex]` — отправка и опциональный regex capture.

### mbus (`ispf-driver-mbus`)

M-Bus meter read (jmbus 3.x). Конфиг: TCP (`host`, `port`) или serial (`serialPort`).

Point mapping: `primary:secondary:register`.

### omron-fins (`ispf-driver-omron-fins`)

FINS/TCP. Конфиг: `host`, `port` (9600), `destNode`, `srcNode`.

Point mapping: `DM:100:1` (area:address:count).

### asterisk (`ispf-driver-asterisk`)

AMI. Конфиг: `host`, `port` (5038), `username`, `secret`.

Point mapping: AMI action block (например `Action: Ping`).

### smpp (`ispf-driver-smpp`)

SMPP 3.x (jsmpp). Конфиг: `host`, `port`, `systemId`, `password`.

Point mapping: `bind` (статус) или `destination:message` (submit).

### smb (`ispf-driver-smb`)

SMB/CIFS (smbj). Конфиг: `host`, `share`, `username`, `password`, `domain`.

Point mapping: путь к файлу в share → `exists`, `size`.

### Отложено (roadmap §10)

CORBA, CWMP, DLMS/COSEM, Ethernet/IP, Graph DB, HTTP Server, IEC 104 Server, OPC DA/bridge, OPC UA Server, SIP, SMI-S, VMware, XMPP, JMS/MQ, Web Transaction, WMI, IPMI, Local Agent, Modem AT, Modbus RTU/UDP, LDAP/DHCP/IMAP/POP3/RADIUS в отдельных драйверах — см. [PLATFORM_DEVELOPER_BACKLOG.md §10](PLATFORM_DEVELOPER_BACKLOG.md#10-каталог-драйверов-устройств-roadmap).

## Добавление своего драйвера

1. Создайте модуль `packages/ispf-driver-xxx`, зависимость на `ispf-driver-api`.
2. Реализуйте `DeviceDriver`.
3. Зарегистрируйте в `DriverCatalog` (`ispf-server`).
4. Добавьте `implementation(project(...))` в `ispf-server/build.gradle.kts`.
5. Определите model с driver-переменными или примените `RELATIVE` модель к `DEVICE`.

## Диагностика

- Логи: `com.ispf.server.driver` (уровень DEBUG в `local`/`dev`)
- `driverStatus` на объекте устройства
- WARN в логе при ошибке poll (SNMP timeout, read-only variable)
