> **Язык:** русская версия (вычитка). Канонический английский: [en/opcua-server-interop.md](../en/opcua-server-interop.md).

# Драйвер сервера OPC UA — Руководство по взаимодействию (BL-143)

Встроенный драйвер **сервера** OPC UA (`opcua-server`) предоставляет переменные устройства ISPF внешним клиентам OPC UA через Eclipse Milo. Партнер по обратной связи для драйвера `opcua` **клиента**.

## Роль на платформе

| Драйвер | Направление | Модуль |
|--------|-----------|--------|
| `opcua-server` | ISPF → OPC UA wire | `packages/ispf-driver-opcua-server` |
| `opcua` | OPC UA wire → ISPF | `packages/ispf-driver-opcua` |

Матрица производства: `DriverProductionMatrix` строка `opcua-server` — зрелость **ПРОИЗВОДСТВО**, возможности **чтения**, **записи**, **качества**.

## Конечная точка провода

| Настройка | По умолчанию | Описание |
|---------|---------|-------------|
| URL | `opc.tcp://localhost:4840/ispf` | Built from `bindPort` + fixed path `/ispf` |
| Transport | `TCP_UASC_UABINARY` | Milo binary protocol |
| Security | `SecurityPolicy.None` | Lab / interop only |
| Авторизация | Аноним или имя пользователя | Валидатор имени пользователя принимает любые учетные данные |
| Application URI | `urn:ispf:driver:opcua-server` | Milo application identity |
| Namespace URI | `urn:ispf:opcua:server` | Custom namespace index (default **2**) |

Constants and helpers: `OpcUaServerInterop.java` in the driver module.

## Адресное пространство

Просмотр дерева:

```text
Objects
  └── IspfVariables
        └── {mapped tags from pointMappings}
```

- Variable type: `BaseDataVariableType`, datatype **String**, read/write.
- Nodes are created lazily on first `readPoints()`.
- Browse path pattern: `Objects/IspfVariables/{TagName}`.

## Конфигурация устройства

| Ключ | По умолчанию | Заметки |
|-----|---------|-------|
| `bindPort` | `4840` | TCP listen port |
| `namespace` | `2` | Namespace index for bare identifiers |
| `timeoutMs` | `5000` | Server startup/shutdown timeout |
| `endpointPath` | `/ispf` | Documented in metadata; not user-overridable in v0.1 |

Отображение точек: строка OPC UA `NodeId`, например. `ns=2;s=Temperature` или пустой `Temperature` (использует настроенное пространство имен).

## Чтение/запись схемы

Driver variable schema `opcUaServerValue`:

| Поле | Описание |
|-------|-------------|
| `value` | String node value |
| `quality` | Always `StatusCode.GOOD` |
| `nodeId` | Mapping text |

Write expects `value` field in the incoming `DataRecord`.

## Тестирование взаимодействия

### Серверный модуль (CI-шлюз)

```bash
./gradlew :packages:ispf-driver-opcua-server:test
```

Обложки:

- `OpcUaServerPointTest` — NodeId parsing
- `OpcUaServerDeviceDriverTest` — connect, read, write round-trip on ephemeral port
- `OpcUaServerSubscriptionWriteBackIntegrationTest` — подписка + запись внешнего клиента Milo → обратная запись переменной ISPF (BL-143)

### Петля клиента + сервера (полный стек)

```bash
./gradlew :packages:ispf-driver-opcua:test
```

`OpcUaDeviceDriverTest` запускает процесс `OpcUaServerDeviceDriver`, затем выполняет просмотр, запись и подписку клиента на `opc.tcp://localhost:{port}/ispf`.

### Отчет о взаимодействии с топ-20

```bash
bash deploy/tools/driver-interop-report.sh
```

CI workflow: `.github/workflows/driver-interop.yml` includes `ispf-driver-opcua-server`.

## Драйвер партнерского клиента

Configure `opcua` client device:

```json
{
  "endpointUrl": "opc.tcp://localhost:4840/ispf",
  "timeoutMs": "10000",
  "readMode": "poll"
}
```

Point mapping: same NodeId as server mapping, e.g. `ns=2;s=Temperature`.

См. также [drivers](drivers.md) § клиент opcua и [driver-interop-lab](driver-interop-lab.md).

## Ограничения (v0.1)

– Только строковые значения (без сопоставления типизированных вариантов).
- SecurityPolicy None — не подходит для ненадежных сетей.
— Нет просмотра REST для серверных устройств (используйте просмотр OPC UA в проводном режиме).
- Уведомления о внешних подписках зависят от обратных вызовов мониторинга сервера Milo (обратная запись в переменные ISPF рассматривается в рамках интеграционного теста BL-143)
- Эфемерный самозаверяющий сертификат восстанавливается при каждом подключении.

## Связанные файлы

| Путь | Цель |
|------|---------|
| `packages/ispf-driver-opcua-server/.../OpcUaServerDeviceDriver.java` | Driver SPI |
| `packages/ispf-driver-opcua-server/.../OpcUaServerInterop.java` | Documented endpoint contract |
| `packages/ispf-driver-opcua-server/.../IspfOpcUaNamespace.java` | Address space |
| `packages/ispf-driver-opcua/.../OpcUaDeviceDriverTest.java` | Cross-driver loopback |
| `packages/ispf-server/.../DriverProductionMatrix.java` | Production gate |
