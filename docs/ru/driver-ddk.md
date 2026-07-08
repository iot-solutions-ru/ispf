> **Язык:** русская версия (вычитка). Канонический английский: [en/driver-ddk.md](../en/driver-ddk.md).

# Комплект разработки драйверов (DDK)

**BL-144** — каркас для пользовательских пакетов драйверов вне ядра monorepo или как новый `ispf-driver-*` модуль.

## Когда использовать

- Партнёрский протокол, не входящий в комплект Apache/GPL.
- Внутренний драйвер заказчика с циклом релизов.
- Prototype перед promotion в `DriverProductionMatrix`.

## Артефакты

| Путь | Описание |
| ---- | -------- |
| [`packages/ispf-driver-ddk`](../packages/ispf-driver-ddk/) | Gradle-модуль DDK (исходники шаблонов + дымовой тест) |
| [`packages/ispf-driver-ddk/template/`](../packages/ispf-driver-ddk/template/) | Копируемый заглушка: драйвер, тест, `driver-pack.json`, `build.gradle.kts` |
| [LICENSED_DRIVER_PACKS.md](licensed-driver-packs.md) | Расположение во время выполнения и соединение |
| [DRIVER_PROMOTION.md](driver-promotion.md) | Чеклист ПРОДАКШН |
| [DRIVER_INTEROP_LAB.md](driver-interop-lab.md) | CI-взаимодействие после продвижения |

## Рабочий процесс

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

**Правило входа:** горячий путь не пишется в БД; только `updateVariable` ([Входной контракт ADR](../packages/ispf-driver-api/src/main/java/com/ispf/driver/DeviceDriver.java)).

### 3. Петлевая проверка

Минимум один JUnit-тест со stub-сервером на `127.0.0.1` (см. `TemplateDeviceDriverTest`). Без hardware в CI.

### 4. Пакет «Собрать»

```bash
./gradlew :packages:ispf-driver-acme-widget:assembleDriverPack
ls build/driver-packs/ispf-driver-acme-widget/
```

### 5. Развертывание пакета

Скопируйте каталог в `${ISPF_DRIVER_PACKS_DIR}/` на расстоянии. Перезапуск ISPF → `LicensedDriverPackLoader` регистрирует драйвер.

### 6. Продвижение

1. Запись в `DriverProductionMatrix` с `loopbackTestSourcePath` и `interopGradleModule`.
2. Добавить модуль в [`.github/workflows/driver-interop.yml`](../.github/workflows/driver-interop.yml).
3. Обновить [DRIVERS.md](drivers.md).

## Соглашение о сопоставлении точек (шаблон)

Формат: `channel:address` (пример `ai:room-1`). Замените на свой DSL (стиль Modbus, OPC NodeId и т.д.).

## Справочные драйверы (roadmap.md)

Принятие BL-144: три эталонных пользовательских драйвера — `template/` (acme-widget), `examples/simple-counter/`, `examples/json-poller/`, `examples/modbus-simulator/`. Все четыре компилируются в `:packages:ispf-driver-ddk:test`.

## Связанные АДР

- [0022-driver-production-matrix.md](decisions/0022-driver-production-matrix.md)
- [0002-dogfooding-gate.md](decisions/0002-dogfooding-gate.md)
