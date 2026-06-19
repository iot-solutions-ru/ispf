# Драйверы устройств

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
| `driverId` | ID драйвера (`virtual`, `mqtt`, `modbus-tcp`, `snmp`) |
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

SNMP4J, v1/v2c GET/SET.

Формат точки: `oid` или `oid:VALUE_KIND` (`STRING`, `INTEGER`, …).

Демо `snmp-localhost`: MIB-II OIDs (`sysName`, `sysDescr`, `sysUpTime`, `sysLocation`).

Конфиг:

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
