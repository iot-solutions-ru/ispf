> **Язык:** русская версия (вычитка). Канонический английский: [en/driver-promotion.md](../en/driver-promotion.md).

# Процесс продвижения драйверов

> **Статус:** Stable — PRODUCTION + ready-for-field. Хаб: [doc-status.md](doc-status.md).

Как перевести драйверы из **stub** / **beta** в **production** (Phase 3.2).

## Метки

| `maturity` | Значение |
|------------|----------|
| `PRODUCTION` | Типовые сценарии, документированный конфиг, тесты |
| `BETA` | Рабочий протокол с ограничениями (платформа, auth, partial stack) |
| `STUB` | Connectivity shell — не для production-телеметрии |

Метка задаётся в `DriverMaturityRegistry` (server) и отдаётся в `GET /api/v1/drivers`.

## Чеклист продвижения

1. Реализовать poll/read (или write, если заявлено) в модуле `ispf-driver-*`.
2. Добавить unit/integration-тесты для парсера точек и happy path.
3. Обновить описание в `DriverMetadata` и раздел в [drivers](drivers.md).
4. Изменить запись в `DriverMaturityRegistry`.
5. При необходимости — демо-устройство / модель в bootstrap.

## Статус (июль 2026, продвижение партии C)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `ethernet-ip` | BETA | **PRODUCTION** | Реальный UCMM CIP-клиент: Read/Write Tag (0x4C/0x4D) для BOOL/SINT/INT/DINT/REAL поверх `SendRRData`; `EthernetIpDeviceDriverTest` (CIP-эмулятор в тесте); `POLL` + `WRITE` + `QUALITY` |
| `vmware` | BETA | **PRODUCTION** | Реальный vSphere SOAP-флоу: RetrieveServiceContent + SessionManager Login (session cookie) + PropertyCollector RetrieveProperties, re-login при NotAuthenticated, Logout при disconnect; `VmwareDeviceDriverTest` переписан вокруг фейкового эндпоинта с проверкой сессии |
| `smi-s` | BETA | **PRODUCTION** | Реальный парсер CIM-XML (JDK DOM/XPath, secure processing) вместо захардкоженных свойств; обработка CIM `ERROR`; `SmisDeviceDriverTest` расширен (значения, массивы, ошибка, отказ соединения) |
| `opc-da`, `opc-bridge`, `corba` | BETA | **BETA** (остаётся) | Объективные блокеры: протокол proxy для opc-da/opc-bridge не определён (Windows DCOM bridge вне скоупа); corba требует стороннюю ORB (JDK CORBA удалён) |

## Статус (июль 2026, продвижение партии B3)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `iec104-server` | BETA | **PRODUCTION** | `Iec104ServerDeviceDriverTest` (j60870 client end-to-end); `POLL` + `WRITE` + `QUALITY` |
| `omron-fins` | — (новый) | **PRODUCTION** | `OmronFinsDeviceDriverTest` (фейковый FINS/TCP-сервер: handshake + чтение памяти); read-only |
| `mbus` | — (новый) | **PRODUCTION** | `MbusDeviceDriverTest` (фейковый M-Bus TCP-счётчик, кадры RSP_UD); read-only |
| `smpp` | — (новый) | **PRODUCTION** | `SmppDeviceDriverTest` (фейковый SMSC: bind + submit_sm); **fix**: source/destination в submit_sm были перепутаны (source = `systemId`, destination = destination точки) |
| `xmpp` | — (новый) | **PRODUCTION** | `XmppDeviceDriverTest` (XMPP-сервер в тесте: SCRAM-SHA-1 + ping end-to-end); **fix**: `smack-xmlparser-xpp3` / `smack-java8` переведены в runtime-зависимости (без них `ExceptionInInitializerError`) |
| `ipmi` | — (новый) | **PRODUCTION** | `IpmiDeviceDriverTest` (RMCP ping + codec seam); **fix**: `readSensor` теперь выполняет реальный `Get Sensor Reading` |
| `wmi` | BETA | **PRODUCTION** | `WmiDeviceDriverTest` (happy-path только на Windows); read-only, только Windows |
| `odbc` | BETA | **PRODUCTION** | `OdbcDeviceDriverTest` (H2 в режиме совместимости с мостом — не реальный ODBC bridge); нужен внешний ODBC-JDBC bridge JAR |

## Статус (июль 2026, продвижение партии B2)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `sip` | BETA | **PRODUCTION** | `SipDeviceDriverTest` (UDP SIP-ответчик); **fix**: OPTIONS был мёртвым кодом (jain-sip-ri отклоняет ListeningPoint на порту 0 — порт Via теперь даёт сырой сокет); `reload4j` перенесён в runtime-зависимости |
| `asterisk` | BETA | **PRODUCTION** | `AsteriskDeviceDriverTest` (AMI-сервер в тесте); read-only |
| `radius` | BETA | **PRODUCTION** | `RadiusDeviceDriverTest` (TinyRadius-сервер в процессе: Accept/Reject/недостижимость); только PAP |
| `ldap` | BETA | **PRODUCTION** | `LdapDeviceDriverTest` (UnboundID InMemoryDirectoryServer); base DN = root DSE |
| `jmx` | BETA | **PRODUCTION** | `JmxDeviceDriverTest` (`JMXConnectorServer` в той же JVM); доки исправлены (нет local-режима, маппинг `::`) |
| `nmea` | BETA | **PRODUCTION** | `NmeaDeviceDriverTest` (TCP ServerSocket GGA/RMC); доки исправлены (только TCP, префиксное совпадение) |
| `message-stream` | BETA | **PRODUCTION** | `MessageStreamDeviceDriverTest`; **fix**: TCP-чтение теперь блокирующее с soTimeout (было `InputStream.available()`) |
| `dhcp` | BETA | **PRODUCTION** | `DhcpDeviceDriverTest` (UDP OFFER-ответчик); **fix**: порты/broadcast-адрес инъектируемы (`serverPort`/`listenPort`/`broadcastAddress`, дефолты прежние) |
| `ingress-syslog` | BETA | **PRODUCTION** | `SyslogIngressDeviceDriverTest` расширен (датаграмма → запись + статистика); **fixes**: `bindAddress` применяется, ingress-буфер пересоздаётся в `connect()`, description больше не обещает парсинг RFC |
| `ingress-snmp-trap` | BETA | **PRODUCTION** | `SnmpTrapIngressDeviceDriverTest` (новый); raw-capture скоуп задокументирован; те же фиксы bindAddress/буфера |
| `ingress-sflow` | BETA | **PRODUCTION** | `SflowIngressDeviceDriverTest` (новый); raw-capture скоуп задокументирован; те же фиксы bindAddress/буфера |

## Статус (июль 2026, продвижение партии B1)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `imap` | BETA | **PRODUCTION** | `ImapDeviceDriverTest` (GreenMail IMAP); read-only |
| `pop3` | BETA | **PRODUCTION** | `Pop3DeviceDriverTest` (GreenMail POP3); read-only |
| `soap` | BETA | **PRODUCTION** | `SoapDeviceDriverTest` (встроенный HttpServer); маппинг = полный envelope |
| `web-transaction` | BETA | **PRODUCTION** | `WebTransactionDeviceDriverTest` (2 шага против встроенного HttpServer); без сессии/assertions между шагами |
| `http-server` | BETA | **PRODUCTION** | `HttpServerDeviceDriverTest`; legacy-capability `write` убрана (заявлялась, но не была реализована) |
| `jdbc` | BETA | **PRODUCTION** | `JdbcDeviceDriverTest` (H2 in-memory); маппинг = полный SELECT на точку |
| `graph-db` | BETA | **PRODUCTION** | `GraphDbDeviceDriverTest` (Gremlin-HTTP loopback); Bolt-ветка требует живой Neo4j |
| `jms` | BETA | **PRODUCTION** | `JmsDeviceDriverTest` (встроенный ActiveMQ `vm://` broker); исправлен баг пересчёта `browseDepth` |

## Статус (июль 2026, продвижение партии A)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `dnp3` | BETA (BL-191) | **PRODUCTION** | Class 0/1/2/3 poll loopback `Dnp3DeviceDriverTest`; `writePoint` по-прежнему не реализован |
| `haystack` | BETA | **PRODUCTION** | `HaystackDeviceDriverTest` (embedded HttpServer JSON grid); только poll/read |
| `kafka` | BETA | **PRODUCTION** | `KafkaDeviceDriverTest`; poll/read, `writePoint` read-only |
| `coap` | BETA | **PRODUCTION** | `CoapDeviceDriverTest` (in-process Californium server) |
| `icmp` | BETA | **PRODUCTION** | `IcmpDeviceDriverTest` (localhost-доступность) |
| `ip-host` | BETA | **PRODUCTION** | `IpHostDeviceDriverTest` (локальные listener'ы + DNS/PING loopback) |
| `telnet` | BETA | **PRODUCTION** | `TelnetDeviceDriverTest`; exit code всегда 0 (ограничение протокола) |
| `modem-at` | BETA | **PRODUCTION** | `ModemAtDeviceDriverTest` (TCP AT-заглушка) |
| `ssh` | BETA | **PRODUCTION** | `SshDeviceDriverTest` (встроенный Apache MINA SSHD); `StrictHostKeyChecking=no` |
| `file` | BETA | **PRODUCTION** | `FileDeviceDriverTest` (JUnit temp dirs) |
| `folder` | BETA | **PRODUCTION** | `FolderDeviceDriverTest` (JUnit temp dirs) |
| `application` | BETA | **PRODUCTION** | `ApplicationDeviceDriverTest`; `timeoutMs` ограничивает ожидание, зависший child убивается |

## Статус (июль 2026, Phase 25 BL-140)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `iec104` | BETA | **PRODUCTION** | Loopback vs `iec104-server`; write commands |
| `dnp3` | BETA | **PRODUCTION** (только poll) | Class 0/1/2/3 poll; **write не реализован** — field write = не готов |
| `dlms` | BETA | **PRODUCTION** | Gurux read/write; auth NONE |
| `ethernet-ip` | BETA | **PRODUCTION** | CIP session registration + tag path loopback |
| `opc-da` | BETA | **PRODUCTION** (shell) | Connectivity shell + parser tests — **не** ready-for-field DA |
| `opc-bridge` | BETA | **PRODUCTION** (shell) | Bridge point mapping + parser tests — полный OPC через внешний bridge |

**Политика:** connectivity shell и read-only master нельзя продавать как field-ready PRODUCTION. См. [Ready-for-field](#ready-for-field-полевые-пилоты) ниже; scorecard OT отслеживает эти пробелы.

## Статус (июнь 2026)

| driverId | Было | Стало | Примечание |
|----------|------|-------|------------|
| `dnp3` | STUB | **BETA** | Class 0/1/2/3 poll via `io.stepfunc:dnp3`; write not implemented |
| `cwmp` | STUB | **PRODUCTION** | Inform + ACS `GetParameterValues`; TR-069 acceptance tests |
| `flexible` | BETA | **PRODUCTION** | TCP/UDP request/response |
| `gps-tracker` | BETA | **PRODUCTION** | GPS/M2M TCP server |
| `corba` | STUB | **BETA** | IIOP TCP reachability + point parser tests |
| `ethernet-ip` | STUB | **BETA** | CIP session registration + tag path mapping |
| `opc-da` | STUB | **BETA** | DCOM/TCP connectivity shell + parser tests |
| `opc-bridge` | STUB | **BETA** | Bridge point mapping + parser tests; полный OPC-стек через внешний bridge |
| `vmware` | STUB | **BETA** | vSphere API point parser + connectivity shell |
| `smi-s` | STUB | **BETA** | SMI-S CIM point parser + connectivity shell |

Остальные stub-драйверы требуют нативный стек или коммерческий pack — продвижение только по конкретному запросу ([licensed-driver-packs](licensed-driver-packs.md)).

## Ready-for-field (полевые пилоты)

**Не автоматически**, когда `maturity: PRODUCTION` или lab interop зелёный. Драйвер/сценарий **ready for field** только после:

1. **Именованной полевой задачи на реализацию** — площадка, протокол, тикет интегратора, scope на доработку или hardening драйвера под этот деплой.
2. Зелёного lab dry-run для сценария ([field-pilot-playbook](field-pilot-playbook.md)).
3. **7-дневного soak** + OT sign-off заказчика.

До пункта (1) статус только **playbook-ready**. См. BL-140 (Partial) и [quality path Wave 1](roadmap.md#quality-path-to-done).
