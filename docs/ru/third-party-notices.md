> **Язык:** русская версия (вычитка). Канонический английский: [en/third-party-notices.md](../en/third-party-notices.md).

# Уведомления о стороннем ПО (Уведомления третьих лиц)

Дистрибутив ISPF включает дополнительные компоненты с лицензиями, бесплатно из открытого кода ISPF. При распространении исходных или двоичных сборок сохраните [LICENSE](../../LICENSE), [NOTICE](../../NOTICE), этот файл и License/Notice-файлы индивидуальных зависимостей.

Настоящий документ является инженерной инвентаризацией, а не юридическим заключением. Для публичного релиза или коммерческой поставки сформируйте SBOM и проведите юридическую проверку.

## Политика мировости ISPF

Код платформы в репозитории лицензируется под **GNU AGPL-3.0** ([0016-agpl-dual-licensing](decisions/0016-agpl-dual-licensing.md)). Сторонние библиотеки сохраняют свои ресурсы.

Текущее состояние поставки:

- **Платформа (`ispf-server`, `web-console`, core packages):** AGPL-3.0; Enterprise — optional `platform-license.json`.
- **Драйверы устройств:** только **пакеты драйверов** в `${ISPF_DRIVER_PACKS_DIR}`; Каждая упаковка — свой `LICENSE` и `licenseType`.
- **Таблица веб-консоли:** встроенная формула движения MIT/AGPL (`ispfSheetEval`); ГиперФормула **не используется**.
- **Пакеты драйверов с авторским левом** (BACnet, DLMS, IEC-104, …): пакеты документов с примечаниями GPL/LGPL — не часть `ispf-server.jar`.
- **Пакеты приложений:** декларативный JSON + лицензионное соглашение клиента; не «исходники платформы» по AGPL.

## Реестр лицензионных рисков

| Компонент | Где используется | Лицензия | Риск / действие |
|-----------|------------------|----------|-----------------|
| `hyperformula` | *(removed)* | — | Replaced by `ispfSheetEval` in web-console (2026-06). |
| `exceljs` | `apps/web-console` (импорт/экспорт электронной таблицы XLSX) | **МИТ** | Ленивая загрузка; только виджет электронной таблицы. |
| `@uiw/react-codemirror`, `@codemirror/*` | `apps/web-console` (script editor) | **MIT** | Direct runtime deps. |
| `cytoscape` | `apps/web-console` (topology/graph views) | **MIT** | Direct runtime dep. |
| `com.infiniteautomation:bacnet4j` | `packages/ispf-driver-bacnet` (пакет драйверов) | **GPL-3.0** | Исключено из профиля развертывания `permissive` по умолчанию. |
| `org.gurux:gurux.dlms`, `org.gurux:gurux.net` | `packages/ispf-driver-dlms` (пакет драйверов) | **GPL-2.0** | Исключено из профиля развертывания `permissive` по умолчанию. |
| `org.openmuc:j60870` | `packages/ispf-driver-iec104*` (пакеты драйверов) | **GPL** | Исключено из профиля развертывания `permissive` по умолчанию. |
| `io.stepfunc:dnp3` | `packages/ispf-driver-dnp3` (пакет драйверов) | **LicenseRef-StepFunc-NonCommercial** | **Не входит в комплект** в упаковке JAR; исключен из `permissive` развертывания; prod требует коммерческой лицензии StepFunc. |
| `fr.jrds:vxIPMI` | `packages/ispf-driver-ipmi` | **GPL-3.0** (Verax) | `licenseType` fixed; excluded from `permissive` deploy. |
| `javax.sip:jain-sip-ri` | `packages/ispf-driver-sip` | **Public Domain** (NIST) | `licenseType`: `LicenseRef-NIST-PublicDomain`. |
| `org.tinyradius:tinyradius` | `packages/ispf-driver-radius` | LGPL | Excluded from default `permissive` deploy profile. |
| `com.fazecast:jSerialComm` | `packages/ispf-driver-modem-at` | Apache-2.0 / LGPL-3.0 dual | Use Apache-2.0 variant in SBOM. |
| `org.openmuc:jmbus` | `packages/ispf-driver-mbus` | MPL-2.0 | Excluded from default `permissive` deploy profile. |
| `bpmn-js` | `apps/web-console`, редактор/просмотрщик BPMN | Лицензия bpmn.io (MIT + водяной знак) | Водяной знак применяется в CSS; проверьте в пользовательском интерфейсе. |
| `@mapbox/jsonlint-lines-primitives` | переходный (maplibre) | **MIT** (переопределить при аудите лицензий) | Проверено вверх по течению. |
| Пакет символов P&ID | `apps/web-console/.../ispf-pid-v1` | **Apache-2.0** (исходное изображение ISA/ISO) | Создано `tools/symbol-pack-isa`; Поставщик импорта WMF устарел. |

## Рекомендация по лицензионному проекту

Модель ISPF (см. [license](license.md), ADR-0016):

1. **Платформа** — AGPL-3.0; использование сети приводит к возникновению обязательств по предложению источника, если не применяется соглашение Enterprise EULA.
2. **Драйверы** — среда выполнения только в пакете; Отступы с авторским левом изолированы в упаковке с пакетом `LICENSE`.
3. **Веб-консоль** — AGPL; В электронной таблице используется встроенный `ispfSheetEval` (без стороннего механизма формул GPL).
4. **Коммерческая/закрытая версия:** Лицензия на корпоративную платформу + попакетные лицензии RSA, где это необходимо; пакеты приложений по отдельному лицензионному соглашению.

## Бэкенд (Java/Gradle)

Источник инвентаризации: `packages/**/build.gradle.kts`, `:packages:ispf-server:runtimeClasspath`, метаданные лицензии Maven POM из локального кэша Gradle.

### Ядро, Выражение, ИИ

| Модуль | Компонент | Лицензия | Примечание |
|--------|-----------|----------|------------|
| `packages/ispf-core` | `com.fasterxml.jackson.core:jackson-annotations:2.21` | Apache-2.0 | Runtime |
| `packages/ispf-expression` | `dev.cel:cel:0.5.1` | Apache-2.0 | Runtime |
| `packages/ispf-ai-api` | `com.fasterxml.jackson.core:jackson-databind:2.21.0` | Apache-2.0 | Runtime |
| `packages/ispf-ai-openai-compatible` | `com.fasterxml.jackson.core:jackson-databind:2.21.0` | Apache-2.0 | Runtime |
| `packages/ispf-ai-ollama` | `com.fasterxml.jackson.core:jackson-databind:2.21.0` | Apache-2.0 | Runtime |

### Сервер

| Компонент | Версия | Лицензия | Примечание |
|-----------|--------|----------|------------|
| Стартеры Spring Boot/Spring Framework | 4.0.7 | Апач-2.0 | Интернет, WebSocket, Проверка, Привод, JPA, Redis, Кэш, Безопасность, Сервер ресурсов OAuth2, Стартер Flyway |
| Весенняя безопасность | 7.0.6 | Апач-2.0 | Транзитивно через сервер ресурсов OAuth2 |
| Драйвер PostgreSQL JDBC | 42.7.11 | Пункт BSD-2 | Время выполнения |
| База данных H2 | 2.4.240 | МПЛ-2.0/ЭПЛ-1.0 двойной | Локальная/тестовая среда выполнения |
| Поддержка Flyway Core/PostgreSQL | 11.14.1 | Апач-2.0 | Миграции |
| Микрометрические измерения/трассировка | 1.16.6/1.6.6 | Апач-2.0 | Прометей, ОТЛП, трассировка моста |
| API/SDK OpenTelemetry Java (переходный) | через микрометр | Апач-2.0 | Наблюдаемость |
| ДНАТС | 2.20.5 | Апач-2.0 | Интеграция НАТС |
| ЯРГ | 2.2.22 | Апач-2.0 | Экспорт отчета |
| Apache POI (переходный через YARG) | под управлением YARG | Апач-2.0 | Экспорт офиса/шаблоны |
| документ4j | 8.3.11 | Апач-2.0 | Поддержка экспорта документов |
| JAXB API/среда выполнения | 2.3.1/2.3.9 | CDDL/GPL с исключением пути к классам/варианты в стиле EDL; проверить точные артефакты в SBOM | XML-привязка |

### Драйверы протоколов

| Модуль | Компонент | Лицензия | Риск / примечание |
|--------|-----------|----------|-------------------|
| `ispf-driver-mqtt` | Eclipse Paho MQTT v3 `1.2.5` | EPL / EDL | Eclipse notice required |
| `ispf-driver-modbus`, `ispf-driver-modbus-rtu`, `ispf-driver-modbus-udp` | `com.ghgande:j2mod:3.2.1` | Apache-2.0 | |
| `ispf-driver-snmp` | `org.snmp4j:snmp4j:3.9.0` | Apache-2.0 | |
| `ispf-driver-coap` | Затмение Калифорния `3.12.0` | EPL/EDL (проверьте точный POM в SBOM) | Требуется уведомление о затмении |
| `ispf-driver-opcua`, `ispf-driver-opcua-server` | Затмение Майло `0.6.15` | EPL-2.0 (проверьте точный POM в SBOM) | Требуется уведомление о затмении |
| `ispf-driver-s7` | `com.github.s7connector:s7connector:2.1` | Apache-2.0 | |
| `ispf-driver-iec104`, `ispf-driver-iec104-server` | `org.openmuc:j60870:1.7.2` | GPL | **Copyleft** — excluded from `permissive` deploy |
| `ispf-driver-bacnet` | `com.infiniteautomation:bacnet4j:6.0.0` | GPL-3.0 | **Copyleft** — excluded from `permissive` deploy |
| `ispf-driver-dlms` | `org.gurux:gurux.dlms:4.0.79`, `org.gurux:gurux.net:1.0.30` | GPL-2.0 | **Copyleft** — excluded from `permissive` deploy |
| `ispf-driver-dnp3` | `io.stepfunc:dnp3:1.6.0` | LicenseRef-StepFunc-Некоммерческая | **Не входит в комплект JAR**; только внешний отдел |
| `ispf-driver-jms` | Клиент Apache ActiveMQ `6.1.8` | Апач-2.0 | Версия разрешена с помощью управления зависимостями |
| `ispf-driver-kafka` | Клиенты Apache Kafka `4.1.2` | Апач-2.0 | Объявлено `3.8.1`, решено до `4.1.2` |
| `ispf-driver-imap`, `ispf-driver-pop3` | Затмение Ангус Почта `2.0.3` | EPL-2.0/GPL с исключением пути к классам (проверьте точный артефакт) | Требуется уведомление о затмении |
| `ispf-driver-telnet` | Apache Commons Net `3.11.1` | Apache-2.0 | |
| `ispf-driver-smb` | SMBJ `0.13.0` | Apache-2.0 | |
| `ispf-driver-modem-at` | jSerialComm `2.11.0` | Двойной Apache-2.0/LGPL-3.0 | Используйте опцию Apache-2.0 |
| `ispf-driver-ldap` | UnboundID LDAP SDK `7.0.1` | Apache-2.0/GPLv2/LGPLv2.1/Лицензия на бесплатное использование | Используйте вариант, совместимый с Apache/бесплатным использованием; проверить на предмет перераспределения |
| `ispf-driver-graph-db` | Neo4j Java Driver `5.26.0` | Apache-2.0 (verify exact POM in SBOM) | |
| `ispf-driver-xmpp` | Smack `4.4.8` (`smack-tcp`, `smack-im`, `smack-extensions`) | Apache-2.0 | |
| `ispf-driver-smpp` | JSMPP `3.0.1` | Apache-2.0 | |
| `ispf-driver-radius` | TinyRadius `1.1.3` | LGPL | LGPL obligations |
| `ispf-driver-mbus` | OpenMUC jMBus `3.3.0` | MPL-2.0 | MPL file-level copyleft |
| `ispf-driver-ssh` | mwiede JSch `0.2.21` | BSD / ISC | |
| `ispf-driver-ipmi` | vxIPMI `2.0.0.1` | GPL-3.0 (Verax) | Excluded from `permissive` deploy |
| `ispf-driver-sip` | JAIN SIP RI `1.3.0-91` | Public Domain (NIST) | Included in `permissive` deploy |

### Модули драйверов в дереве без сторонних библиотек непосредственно во время выполнения

Следующие модули сейчас не объявляют про сторонние зависимости во время выполнения в `build.gradle.kts` сверх модулей проекта ISPF и тестовых библиотек: `ispf-driver-api`, `ispf-driver-virtual`, `ispf-driver-http`, `ispf-driver-icmp`, `ispf-driver-jdbc`, `ispf-driver-file`, `ispf-driver-folder`, `ispf-driver-application`, `ispf-driver-message-stream`, `ispf-driver-nmea`, `ispf-driver-soap`, `ispf-driver-ip-host`, `ispf-driver-gps-tracker`, `ispf-driver-flexible`, `ispf-driver-ethernet-ip`, `ispf-driver-omron-fins`, `ispf-driver-asterisk`, `ispf-driver-corba`, `ispf-driver-opc-da`, `ispf-driver-opc-bridge`, `ispf-driver-odbc`, `ispf-driver-cwmp`, `ispf-driver-web-transaction`, `ispf-driver-http-server`, `ispf-driver-vmware`, `ispf-driver-smis`, `ispf-driver-dhcp`, `ispf-driver-wmi`.

### Зависимости Java только для тестирования

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| JUnit Юпитер / Платформа JUnit | ЭПЛ-2.0 | Только тест |
| УтверждатьJ | Апач-2.0 | Только тест |
| Мокито | Массачусетский технологический институт | Только тест; проверить точный разрешенный артефакт в SBOM |
| Стартеры тестов Spring Boot | Апач-2.0 | Только тест |

### Плагины Gradle во время сборки

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| Градл | Апач-2.0 | Инструмент сборки |
| `org.springframework.boot` Gradle plugin | Apache-2.0 | Build only |
| `io.spring.dependency-management` Gradle plugin | Apache-2.0 | Build only |
| `org.gradle.toolchains.foojay-resolver-convention` | Apache-2.0 (verify in SBOM) | Build only |

## Веб-консоль (npm)

Источник инвентаризации: `apps/web-console/package.json`, `apps/web-console/package-lock.json`.

### Прямые зависимости времени выполнения

| Компонент | Версия в файле блокировки | Лицензия | Примечание |
|-----------|-------------------|----------|------------|
| `@tanstack/react-query` | 5.101.0 | MIT | |
| `@tanstack/react-virtual` | 3.14.3 | MIT | |
| `bpmn-auto-layout` | 1.3.0 | MIT | |
| `bpmn-js` | 18.18.0 | bpmn.io license | Watermark enforced in CSS |
| `@codemirror/lang-java` | 6.0.2 | MIT | Script editor |
| `@uiw/react-codemirror` | 4.25.10 | MIT | Script editor |
| `@uiw/codemirror-theme-vscode` | 4.25.10 | MIT | Script editor theme |
| `cytoscape` | 3.34.0 | MIT | Graph/topology widget |
| `exceljs` | 4.4.0 | MIT | Spreadsheet XLSX (lazy) |
| `i18next` | 26.3.1 | MIT | |
| `maplibre-gl` | 5.24.0 | BSD-3-Clause | |
| `react` | 19.2.7 | MIT | |
| `react-dom` | 19.2.7 | MIT | |
| `react-i18next` | 17.0.8 | MIT | |
| `react-map-gl` | 8.1.1 | MIT | |
| `react-router-dom` | 7.18.0 | MIT | |
| `recharts` | 3.8.1 | MIT | |

### Сводка переходной лицензии среды выполнения

| Лицензия | Количество пакетов |
|----------|--------------------|
| Массачусетский технологический институт | 95 |
| ИСЦ | 22 |
| Пункт BSD-3 | 6 |
| Пункт BSD-2 | 3 |
| Апач-2.0 | 3 |
| MIT ИЛИ Apache-2.0 | 1 |
| Массачусетский технологический институт и ISC | 1 |
| ПОСМОТРЕТЬ ЛИЦЕНЗИЮ В ЛИЦЕНЗИИ | 1 |
| НЕИЗВЕСТНО | 1 |

Специальные пакеты npm:

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| `bpmn-js` | bpmn.io license | Requires visible bpmn.io watermark |
| `@mapbox/jsonlint-lines-primitives` | MIT | Transitive via maplibre; audit override |

### Зависимости npm только для разработчиков

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| Vite / `@vitejs/plugin-react` | MIT | Build only |
| TypeScript | Апач-2.0 | Только сборка |
| Витест | Массачусетский технологический институт | Только тест |
| Пакеты типа React | Массачусетский технологический институт | Только сборка |

## bpmn-js — дополнительное условие

Из лицензии bpmn-js (Camunda Services GmbH):

> Исходный код, отвечающий за отображение водяного знака проекта bpmn.io... НЕ ДОЛЖЕН удаляться или изменяться. Когда это программное обеспечение используется на веб-сайте или в приложении, водяной знак должен оставаться полностью видимым и визуально не перекрываться другими элементами.

Нарушение условий прекращения права использования bpmn-js.

## Инфраструктура (Docker Compose / VPS)

Образы PostgreSQL, Redis, NATS, Mosquitto, Keycloak, ClickHouse и других инфраструктурных контейнеров контроля по лицензиям соответствующих проектов и не являются исходным кодом ISPF. При отправке образа устройства/VM их лицензию также необходимо добавить в пакет SBOM/notice.

## Генерация SBOM и CI перед релизом

Автоматическая проверка (CI + локально):

```bash
node tools/license-audit/check-all.mjs
```

Минимальные команды для ручной инвентаризации:

```bash
./gradlew :packages:ispf-server:dependencies --configuration runtimeClasspath
./gradlew :packages:ispf-server:dependencies --configuration testRuntimeClasspath
cd apps/web-console && npm ls --all
```

Рекомендуемый релизный процесс:

1. Сформировать CycloneDX/SPDX SBOM для Gradle и npm.
2. Проверьте все `UNKNOWN` лицензии вручную.
3. Удалить/заменить GPL-компоненты из AGPL/основного профиля или явно маркировать поставку как авторское лево/содержащие GPL.
4. Приложить `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, npm/Java SBOM и исходные файлы лицензий к бинарному распространению.
