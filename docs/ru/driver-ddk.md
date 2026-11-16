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
| [`packages/ispf-driver-ddk/template/`](../../packages/ispf-driver-ddk/template/) | Заготовка нового модуля: класс драйвера, модульный тест, паспорт `driver-pack.json`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` |
| [Лицензируемые пакеты драйверов](licensed-driver-packs.md) | Упаковка, цифровая подпись и установка внешних драйверов на сервер |

---

## 2. Пошаговый порядок создания драйвера

### Шаг 1. Соберите шаблон отдельно от этого репозитория

`packages/ispf-driver-ddk/template/` — своя сборка Gradle. Координаты `com.ispf:ispf-core` и `com.ispf:ispf-driver-api` появляются в локальном Maven после публикации из checkout ISPF:

```bash
./gradlew publishDdkApi
```

Версия в `~/.m2` равна `version` в `gradle.properties` этого репозитория. Скопируйте `template/` к себе и поставьте ту же версию в `ispfVersion`. Нужны JDK 25 и Gradle 9.9:

```bash
gradle test
gradle assembleDriverPack
```

Каталог пакета: `build/driver-pack/ispf-driver-acme-widget/` — в нём `driver-pack.json` и `ispf-driver-acme-widget.jar`. `assembleDriverPack` собирает fat jar: сторонние `implementation`-библиотеки входят в jar; `com.ispf:*` остаётся `compileOnly`, потому что SPI уже на сервере. При переименовании меняют `packId` в `build.gradle.kts`, `rootProject.name` в `settings.gradle.kts`, а в `driver-pack.json` — `packId`, `drivers[].driverId` и `drivers[].driverClass`. `assembleDriverPack` кладёт jar с именем `$packId.jar`; `jarFile` равен этому имени. `driverClass` — класс, который загружает сервер.

Модуль, который живёт в дереве ISPF, — отдельный каталог `packages/ispf-driver-<id>`, зависимость `project(":packages:ispf-driver-api")` и строка в `gradle/driver-packs.json`. Плагин `ispf-driver-pack` корневая сборка вешает только на такие модули. `template/` в корневой `settings.gradle.kts` не регистрируют.

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

Вне репозитория каталог уже собран шагом 1: `build/driver-pack/ispf-driver-acme-widget/`.

Для модуля внутри дерева, после строки в `gradle/driver-packs.json`:

```bash
./gradlew :packages:ispf-driver-acme-widget:assembleDriverPack
```

Пакет лежит в `packages/ispf-driver-acme-widget/build/driver-pack/ispf-driver-acme-widget/`. `syncAllDriverPacks` копирует в корневой `build/driver-packs/` каждый пакет из `gradle/driver-packs.json`. `syncDevDriverPacks` копирует только модули из списка `devDriverPackProjectNames` в корневом `build.gradle.kts`.

Скопируйте собранную папку пакета в каталог внешних драйверов на сервере (`${ISPF_DRIVER_PACKS_DIR}/`) и перезапустите `ispf-server` — загрузчик `LicensedDriverPackLoader` автоматически подключит новый драйвер в каталог `GET /api/v1/drivers`.

**Связанные разделы:** [Справочник драйверов](drivers.md) · [Уровни зрелости драйверов](driver-promotion.md) · [Лицензируемые пакеты драйверов](licensed-driver-packs.md).
