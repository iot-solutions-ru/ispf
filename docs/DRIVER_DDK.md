# Driver Development Kit (DDK)

**BL-144** — scaffold для custom driver packs вне core monorepo или как новый `ispf-driver-*` модуль.

## Когда использовать

- Партнёрский протокол, не входящий в Apache/GPL bundle.
- Внутренний драйвер заказчика с отдельным release cycle.
- Prototype перед promotion в `DriverProductionMatrix`.

## Артефакты

| Путь | Описание |
| ---- | -------- |
| [`packages/ispf-driver-ddk`](../packages/ispf-driver-ddk/) | Gradle-модуль DDK (template sources + smoke test) |
| [`packages/ispf-driver-ddk/template/`](../packages/ispf-driver-ddk/template/) | Копируемый stub: driver, test, `driver-pack.json`, `build.gradle.kts` |
| [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md) | Runtime layout и лицензирование |
| [DRIVER_PROMOTION.md](DRIVER_PROMOTION.md) | Чеклист PRODUCTION |
| [DRIVER_INTEROP_LAB.md](DRIVER_INTEROP_LAB.md) | CI interop после promotion |

## Workflow

### 1. Создать модуль

```bash
cp -r packages/ispf-driver-ddk/template packages/ispf-driver-acme-widget
```

Отредактируйте `driverId`, package, `DriverMetadata`, point mapping parser.

Добавьте в `settings.gradle.kts`:

```kotlin
"packages:ispf-driver-acme-widget",
```

Модуль автоматически получит plugin `ispf-driver-pack` (см. root `build.gradle.kts`).

### 2. Реализовать SPI

`DeviceDriver` ([`ispf-driver-api`](../packages/ispf-driver-api/src/main/java/com/ispf/driver/DeviceDriver.java)):

- `metadata()` — `driverId`, config schema, maturity
- `initialize` / `connect` / `disconnect`
- `readPoints(Map<variableName, pointMapping>)`
- `writePoint` — если заявлен capability `write`

**Ingress rule:** hot path не пишет в БД; только `updateVariable` ([ADR ingress contract](../packages/ispf-driver-api/src/main/java/com/ispf/driver/DeviceDriver.java)).

### 3. Loopback test

Минимум один JUnit-тест со stub-сервером на `127.0.0.1` (см. `TemplateDeviceDriverTest`). Без hardware в CI.

### 4. Собрать pack

```bash
./gradlew :packages:ispf-driver-acme-widget:assembleDriverPack
ls build/driver-packs/ispf-driver-acme-widget/
```

### 5. Deploy pack

Скопируйте каталог в `${ISPF_DRIVER_PACKS_DIR}/` на сервере. Перезапуск ISPF → `LicensedDriverPackLoader` регистрирует драйвер.

### 6. Promotion

1. Запись в `DriverProductionMatrix` с `loopbackTestSourcePath` и `interopGradleModule`.
2. Добавить модуль в [`.github/workflows/driver-interop.yml`](../.github/workflows/driver-interop.yml).
3. Обновить [DRIVERS.md](DRIVERS.md).

## Point mapping convention (template)

Формат: `channel:address` (пример `ai:room-1`). Замените на свой DSL (Modbus-style, OPC NodeId, и т.д.).

## Reference drivers (roadmap)

BL-144 acceptance (wave 2): три reference custom drivers в `examples/driver-ddk-*`. Сейчас — один compilable template stub в DDK module.

## Связанные ADR

- [0022-driver-production-matrix.md](decisions/0022-driver-production-matrix.md)
- [0002-dogfooding-gate.md](decisions/0002-dogfooding-gate.md)
