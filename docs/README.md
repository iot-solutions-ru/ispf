# Документация ISPF

**IoT Solutions Platform Framework** — middleware-платформа для IoT, SCADA и промышленной автоматизации.

## Содержание

| Раздел | Описание |
|--------|----------|
| [Быстрый старт](GETTING_STARTED.md) | Установка, профили, первый запуск |
| [Архитектура](ARCHITECTURE.md) | Видение, слои, расширяемость |
| [Модель объектов](OBJECT_MODEL.md) | Дерево, переменные, события, функции, CEL |
| [REST API](API.md) | Полный справочник endpoints |
| [Приложения (REQ-PF)](APPLICATIONS.md) | Deploy функций, миграций, bundle, BFF, scheduler |
| [Отчёты (REQ-PF-12)](REPORTS.md) | SQL reports, CSV export, operator manifest |
| [Backlog разработчика platform](PLATFORM_DEVELOPER_BACKLOG.md) | Статус REQ-PF, gap, sprint roadmap, каталог драйверов (§10) |
| [WebSocket](API.md#websocket) | Live-обновления объектов |
| [Модели (Models)](MODELS.md) | Шаблоны объектов, типы, встроенные модели |
| [Драйверы](DRIVERS.md) | 31 встроенных драйверов — SPI, runtime, каталог §10 |
| [Дашборды и виджеты](DASHBOARDS.md) | HMI builder, 14 типов виджетов, layout JSON |
| [Workflow / BPMN](WORKFLOWS.md) | Движок, ISPF-расширения, work queue |
| [Автоматизация](AUTOMATION.md) | События, alert rules, correlators |
| [Web Console](WEB_CONSOLE.md) | Админка, operator HMI, роли |
| [Безопасность](SECURITY.md) | RBAC, Keycloak, профили |
| [Развёртывание](DEPLOYMENT.md) | Docker, переменные окружения, профили Spring |
| [Тестирование](TESTING.md) | Unit, integration, smoke |
| [Лицензия](LICENSE.md) | Apache 2.0 ядро; коммерческие плагины отдельно |
| [Плагины и границы](PLUGINS.md) | Что не входит в `main` |
| [Third-party](THIRD_PARTY_NOTICES.md) | Лицензии зависимостей (bpmn-js, Spring, …) |

## Быстрые ссылки

- Репозиторий: монорепозиторий Gradle + npm
- API: `http://localhost:8080/api/v1`
- Health: `GET /actuator/health`
- Web Console: `http://localhost:5173` (dev)
- Operator HMI: `http://localhost:5173?mode=operator`

## Демо-объекты (после первого запуска)

| Путь | Тип | Модель |
|------|-----|--------|
| `root.platform.devices.demo-sensor-01` | DEVICE | `mqtt-sensor-v1` |
| `root.platform.devices.snmp-localhost` | DEVICE | `snmp-agent-v1` |
| `root.platform.dashboards.demo-sensor` | DASHBOARD | `dashboard-v1` |
| `root.platform.workflows.demo-alarm-handler` | WORKFLOW | `workflow-v1` |

## Структура репозитория

```
packages/
  ispf-core/              # Домен: ObjectTree, DataRecord, PlatformObject
  ispf-expression/        # Google CEL, BindingEvaluator
  ispf-driver-api/        # SPI DeviceDriver
  ispf-driver-virtual/    # Simulator profiles
  ispf-driver-mqtt/       # MQTT (Paho)
  ispf-driver-modbus/     # Modbus TCP (j2mod)
  ispf-driver-snmp/       # SNMP v1/v2c/v3 (SNMP4J)
  ispf-driver-http/       # HTTP client
  ispf-driver-icmp/       # Ping / reachability
  ispf-driver-ssh/        # SSH commands
  ispf-driver-coap/       # CoAP client
  ispf-driver-opcua/      # OPC UA (Milo)
  ispf-driver-s7/         # Siemens S7
  ispf-driver-iec104/     # IEC 60870-5-104
  ispf-driver-bacnet/     # BACnet/IP
  ispf-driver-dnp3/       # DNP3 TCP (placeholder)
  ispf-driver-jmx/        # JMX
  ispf-driver-jdbc/       # SQL JDBC
  ispf-driver-file/       # File system
  ispf-driver-folder/     # Folder listing
  ispf-driver-application/ # Shell/script
  ispf-driver-message-stream/ # TCP/UDP stream
  ispf-driver-nmea/       # NMEA 0183
  ispf-driver-telnet/     # Telnet
  ispf-driver-soap/       # SOAP
  ispf-driver-ip-host/    # IT monitoring (ping, http, dns, …)
  ispf-driver-kafka/      # Apache Kafka
  ispf-driver-gps-tracker/ # GPS/M2M TCP server
  ispf-driver-flexible/   # Flexible TCP/UDP
  ispf-driver-mbus/       # M-Bus
  ispf-driver-omron-fins/ # Omron FINS
  ispf-driver-asterisk/   # Asterisk AMI
  ispf-driver-smpp/       # SMPP
  ispf-driver-smb/        # SMB/CIFS
  ispf-plugin-model/      # Models plugin
  ispf-plugin-workflow/   # BPMN workflow engine (library)
  ispf-server/            # Spring Boot API
apps/
  web-console/            # React 19 + Vite
docs/                     # Эта документация
deploy/                   # Mosquitto config
docker-compose.yml        # PostgreSQL, Redis, NATS, MQTT, Keycloak
```

## Лицензия и границы

- **Ядро** (`main`): [Apache 2.0](LICENSE) + [NOTICE](../NOTICE) — `packages/ispf-*`, web-console, docs
- **Коммерческие плагины и app bundle**: отдельные репозитории, явная лицензия в пакете — [docs/PLUGINS.md](docs/PLUGINS.md)
- Подробнее: [docs/LICENSE.md](docs/LICENSE.md), [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)
