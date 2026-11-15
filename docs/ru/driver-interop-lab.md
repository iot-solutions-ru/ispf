> **Язык:** русская версия. Английский оригинал: [en/driver-interop-lab.md](../en/driver-interop-lab.md).

# Лаборатория совместимости промышленных драйверов

> **Статус:** Stable — автоматические кольцевые тесты обмена (чтение и запись) для 20 основных промышленных протоколов и стенд с эмуляторами в Docker. Метки зрелости: [doc-status.md](doc-status.md).

Лаборатория совместимости проверяет работу основных промышленных драйверов ISPF против программных эмуляторов оборудования при каждом изменении кода драйверов.

---

## 1. Матрица 20 основных промышленных драйверов (`TOP_20_INDUSTRIAL`)

Список контролируется классом `DriverProductionMatrix.TOP_20_INDUSTRIAL`:

| Идентификатор `driverId` | Модуль Gradle | Автоматический тест обмена | Проверка записи (`writePoint`) |
|--------------------------|---------------|----------------------------|--------------------------------|
| `virtual` | `ispf-driver-virtual` | `VirtualUnifiedProfileTest` | Внутренняя запись в память |
| `mqtt` | `ispf-driver-mqtt` | `MqttDeviceDriverTest` | Публикация в топик MQTT |
| `modbus-tcp` | `ispf-driver-modbus` | `ModbusTcpDeviceDriverTest` | Запись регистров и катушек (`FC5`, `FC6`, `FC16`) |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | `ModbusRtuDeviceDriverTest` | Запись кадров RTU |
| `modbus-udp` | `ispf-driver-modbus-udp` | `ModbusPointTest` | Запись кадров UDP |
| `opcua` | `ispf-driver-opcua` | `OpcUaDeviceDriverTest` | Запись узлов через Eclipse Milo (`writeValue`) |
| `opcua-server` | `ispf-driver-opcua-server` | `OpcUaServerSubscriptionWriteBackIntegrationTest` | Приём записи от внешнего OPC UA клиента |
| `snmp` | `ispf-driver-snmp` | `SnmpDeviceDriverTest` | Чтение OID (`GET` / `WALK`) |
| `bacnet` | `ispf-driver-bacnet` | `BacnetDeviceDriverNetworkTest` | Запись свойства `Present_Value` |
| `s7` | `ispf-driver-s7` | `S7DeviceDriverTest` | Запись в блоки данных (`DB`) контроллера S7 |
| `http` | `ispf-driver-http` | `HttpDeviceDriverTest` | HTTP-опрос и разбор JSON |
| `flexible` | `ispf-driver-flexible` | `FlexiblePointTest` | Универсальный обмен TCP/UDP |
| `iec104` | `ispf-driver-iec104` | `Iec104DeviceDriverTest` | Команды телеуправления (прямые и с предварительным выбором `SBO`) |
| `iec104-server` | `ispf-driver-iec104-server` | `Iec104ServerDeviceDriverTest` | Ведомая станция МЭК 60870-5-104 |
| `dnp3` | `ispf-driver-dnp3` | `Dnp3DeviceDriverTest` | Команды управления `CROB` и аналоговые уставки (`g41v3`, включая `SBO`) |
| `dlms` | `ispf-driver-dlms` | `DlmsDeviceDriverTest` | Чтение и запись объектов СПОДЭС / DLMS (`SET`) |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | `EthernetIpDeviceDriverTest` | Чтение и запись символьных тегов CIP (`0x4C` / `0x4D`) |
| `opc-da` | `ispf-driver-opc-da` | `OpcDaDeviceDriverTest` | Статус `BETA` (требуется внешний мост DCOM) |
| `opc-bridge` | `ispf-driver-opc-bridge` | `OpcBridgeDeviceDriverTest` | Статус `BETA` (работа через внешний мост) |
| `gps-tracker` | `ispf-driver-gps-tracker` | `GpsTrackerPointTest` | Приём координат трекеров по TCP |

---

## 2. Как запустить проверку драйверов локально

Проверить один конкретный драйвер:

```bash
./gradlew :packages:ispf-driver-iec104:test
```

Прогнать все 20 промышленных модулей и сформировать сводный отчёт:

```bash
bash deploy/tools/driver-interop-report.sh
```

Результаты сохраняются в каталог `build/driver-interop/`:
- `interop-report.tsv` — таблица с временем выполнения и числом тестов по каждому модулю;
- `interop-summary.md` — сводная таблица в формате Markdown.

---

## 3. Стендовые эмуляторы протоколов в Docker (`deploy/driver-interop/`)

Для ручной проверки и отладки с реальными сетевыми контейнерами в репозитории подготовлен стек эмуляторов:

```bash
# Запустить контейнеры эмуляторов
docker compose -f deploy/driver-interop/docker-compose.yml up -d

# Прогнать быструю проверку связи с эмуляторами
bash deploy/tools/driver-interop-smoke.sh

# Остановить стенд после работы
docker compose -f deploy/driver-interop/docker-compose.yml down
```

| Эмулятор | Образ Docker | Адрес на локальном хосте | Соответствующий драйвер ISPF |
|----------|--------------|--------------------------|------------------------------|
| Брокер MQTT | `eclipse-mosquitto:2.0` | `tcp://127.0.0.1:1883` | `mqtt` (`brokerUrl`) |
| Сервер Modbus TCP | `oitc/modbus-server` | `127.0.0.1:502` | `modbus-tcp` (`host`, `port`) |
| Симулятор ПЛК OPC UA | `mcr.microsoft.com/iotedge/opc-plc` | `opc.tcp://127.0.0.1:4840` | `opcua` (`endpointUrl`, в лаборатории без шифрования `SecurityPolicy.None`) |

**Связанные разделы:** [Справочник драйверов](drivers.md) · [Уровни зрелости драйверов](driver-promotion.md) · [SDK разработки драйверов (DDK)](driver-ddk.md).
