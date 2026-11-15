> **Язык:** русская версия. Английский оригинал: [en/driver-ddk.md](../en/driver-ddk.md).

# Комплект разработки собственных драйверов (Driver DDK)

> **Статус:** Stable — шаблон и инструкция по созданию собственных модулей протокольных драйверов для ISPF. Метки зрелости: [doc-status.md](doc-status.md).

Модуль `packages/ispf-driver-ddk` содержит готовый шаблон и примеры для быстрой разработки новых протокольных драйверов — как внутри репозитория, так и в виде отдельных подключаемых пакетов (`driver-pack`).

---

## 1. Когда нужен собственный драйвер

- Подключение специализированного или фирменного протокола контроллера/счётчика, которого ещё нет в стандартном каталоге.
- Выпуск закрытого коммерческого драйвера с собственной лицензией и циклом обновлений.

| Каталог в репозитории | Назначение |
|-----------------------|------------|
| [`packages/ispf-driver-ddk/`](../../packages/ispf-driver-ddk/) | Модуль DDK с тестами и четырьмя учебными примерами (`template/`, `examples/simple-counter/`, `examples/json-poller/`, `examples/modbus-simulator/`) |
| [`packages/ispf-driver-ddk/template/`](../../packages/ispf-driver-ddk/template/) | Заготовка нового модуля: класс драйвера, модульный тест, паспорт `driver-pack.json` и `build.gradle.kts` |
| [Лицензируемые пакеты драйверов](licensed-driver-packs.md) | Упаковка, цифровая подпись и установка внешних драйверов на сервер |

---

## 2. Пошаговый порядок создания драйвера

### Шаг 1. Скопируйте шаблон модуля

```bash
cp -r packages/ispf-driver-ddk/template packages/ispf-driver-acme-meter
```

Задайте свой идентификатор `driverId`, имя Java-пакета и описание параметров в `DriverMetadata`, а затем добавьте модуль в `settings.gradle.kts`:

```kotlin
"packages:ispf-driver-acme-meter",
```

### Шаг 2. Реализуйте интерфейс `DeviceDriver`

Интерфейс находится в модуле [`packages/ispf-driver-api`](../../packages/ispf-driver-api/src/main/java/com/ispf/driver/DeviceDriver.java):
- `metadata()` — идентификатор драйвера `driverId`, схема настроек подключения и уровень зрелости;
- `initialize(...)`, `connect()`, `disconnect()` — управление соединением с устройством;
- `readPoints(Map<String, String> pointMappings)` — чтение значений по списку привязанных переменных;
- `writePoint(String pointMapping, Object value)` — запись уставки или команды в оборудование (если драйвер поддерживает запись).

> **Главное правило производительности:** код драйвера никогда не должен обращаться к базе данных сервера напрямую — передавайте прочитанные значения только через штатный контракт драйвера (`readPoints` / обратный вызов обновления переменной).

### Шаг 3. Напишите интеграционный тест на локальном сокете

Добавьте JUnit-тест, поднимающий в памяти имитатор вашего протокола на `127.0.0.1` и проверяющий чтение и запись точек (по образцу `TemplateDeviceDriverTest`).

### Шаг 4. Соберите и подключите пакет драйвера

```bash
./gradlew :packages:ispf-driver-acme-meter:assembleDriverPack
ls build/driver-packs/ispf-driver-acme-meter/
```

Скопируйте собранную папку пакета в каталог внешних драйверов на сервере (`${ISPF_DRIVER_PACKS_DIR}/`) и перезапустите `ispf-server` — загрузчик `LicensedDriverPackLoader` автоматически подключит новый драйвер в каталог `GET /api/v1/drivers`.

**Связанные разделы:** [Справочник драйверов](drivers.md) · [Уровни зрелости драйверов](driver-promotion.md) · [Лицензируемые пакеты драйверов](licensed-driver-packs.md).
