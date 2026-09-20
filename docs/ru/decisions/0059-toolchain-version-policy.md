> **Язык:** перевод. Каноническая английская версия: [en/decisions/0059-toolchain-version-policy.md](../../en/decisions/0059-toolchain-version-policy.md).

# ADR-0059: Политика версий тулчейна и зависимостей

## Статус

Accepted (2026-09-20)

## Контекст

Анализ кода в сентябре 2026 отметил стек как «на самом краю»: Spring Boot 4.1, Java 25, TypeScript 7.0, Vite 8, React 19, antd 6, Gradle 9.7. Симптомы уже видны в сборке:

- `build.gradle.kts` принудительно фиксирует `protobuf-java` 4.36.x (CEL vs OTel), `kafka` 4.3.1 и `netty-bom` для тестов — каждый пин обходит несовместимость upstream.
- `hadoop-common` 3.5.0 подтянут в `ispf-server` только ради экспорта Parquet — крупнейшее транзитивное дерево и поверхность CVE в JAR.
- TypeScript 7.0 (Go-компилятор) не имеет программного API, `typescript-eslint` с ним не работает; консоли нужен side-by-side алиас `@typescript/typescript6`.
- Нет письменного правила, брать ли новый мажор в неделю выхода или ждать.

ISPF — self-hosted промышленная SCADA: операторы ждут долгоживущих установок, air-gap обновлений и предсказуемых требований к JDK.

## Решение

### 1. Две полосы

| Полоса | Компоненты | Политика |
|--------|------------|----------|
| **Runtime-контракт** | JDK, мажор Spring Boot, мажор PostgreSQL/TimescaleDB, Node LTS для сборки консоли | **N или N-1 LTS/GA**. Новый мажор берётся только после первого патч-релиза (x.y.1+) *и* после того, как предыдущий мажор поддерживался в ISPF минимум один минорный релиз. Фиксируется в [getting-started](../getting-started.md) и release notes. |
| **Build/dev-тулинг** | Gradle, Vite, Vitest, Playwright, ESLint, TypeScript | Может идти по latest, если `npm ci` / `./gradlew` на чистой машине работает с зафиксированными lock-файлами. Поломка — падение CI, а не проблема оператора. |

### 2. JDK

- Поддерживаемый runtime JDK: **25 (текущий)**; предыдущий LTS (21) — только build-target там, где это явно нужно (edge agent), иначе не поддерживается.
- Смена JDK требует: подтверждённой поддержки JaCoCo, Byte Buddy/Mockito и Spring Boot; обновления `Dockerfile`, portable zip и `deploy/` в том же PR.

### 3. Пины — это долг с владельцем

Каждый `force(...)`, `extra["*.version"]`, `enforcedPlatform` или npm `overrides` обязан иметь комментарий: (а) причина, (б) upstream-issue или релиз, после которого пин можно снять. Владелец пина пересматривает его на каждом миноре Spring Boot.

#### Реестр пинов

Единственный список живых пинов. PR, добавляющий пин, добавляет строку; PR, убирающий upstream-причину, удаляет её. Владелец — команда области, которая ревьюит Dependabot-PR по этому пакету.

| Пин | Где | Зачем | Убрать, когда | Владелец | Пересмотр |
|-----|-----|-------|---------------|----------|-----------|
| `protobuf-java(-util,-javalite)` **4.36.1** (`force`) | корневой `build.gradle.kts` | CEL 0.14+ gencode требует runtime ≥ gencode; Micrometer/OTel транзитивно тянут 4.34.x | Micrometer OTLP и CEL в Boot BOM на одной линии protobuf | platform/core | каждый minor Spring Boot |
| `kafka.version` **4.3.1** (`extra`) + явные `kafka-clients` / `embedded-kafka_2.13` 4.3.1 | `ispf-server`, `ispf-driver-kafka` | Boot BOM форсит 4.2.1 → `CompressionType` CNFE против EmbeddedKafka 4.3.1 | Spring Boot BOM переходит на Kafka 4.3+ | drivers/messaging | каждый minor Spring Boot |
| `netty-bom` **4.2.17/4.2.18.Final** (`enforcedPlatform`) | `ispf-driver-opcua(-server)`, `ispf-driver-mqtt`, `ispf-driver-sparkplug-b`, тесты `ispf-server` | Milo 0.6.x и Moquette 0.17 объявляют Netty 4.1.x; bom поднимает весь граф на патченную линию 4.2 (поток CVE от Dependabot) | релизы Milo / Moquette сами объявят Netty 4.2; тогда убрать bom или свести к одной версии | drivers/OPC UA | ежеквартально |
| `commons-beanutils` **1.11.0** (`constraints`) | `ispf-export-parquet` | hadoop-common 3.5.0 транзитивно тянет старую уязвимую линию | hadoop-common ≥ 3.5.1 объявит ≥ 1.11 | platform/historian | каждый bump hadoop |
| `typescript` → алиас `@typescript/typescript6`; `@typescript/native` = TS 7 | `apps/web-console/package.json` | у `typescript-eslint` нет поддержки API TS 7 (§5) | typescript-eslint поддержит TS 7 | web-console | каждый major typescript-eslint |
### 4. Тяжёлые опциональные зависимости — в отдельные модули

Зависимость, обслуживающая одну фичу и превышающая ~10 МБ транзитивных jar (Hadoop/Parquet, LibreOffice bridge, Cassandra driver, …), живёт в собственном модуле или driver pack, подключается через существующий механизм паков/плагинов и исключается из дефолтного `bootJar`. Сделано: **`ispf-export-parquet`** держит parquet-mr, Avro и hadoop-common. `ispf-server` видит только SPI `HistoryParquetExporter` из `ispf-core` и находит модуль через `ServiceLoader`. По умолчанию модуль включён как `runtimeOnly` (поведение не меняется); `./gradlew bootJar -Pispf.exportParquet=false` собирает сервер без него — тогда `GET …/history/export?format=parquet` отвечает **501**, а cold archive возвращает `skipped`. Следующие кандидаты: Cassandra `java-driver-core`, POI.

### 5. Разделение компилятора фронтенда

`apps/web-console` использует **TypeScript 7 для `tsc -b` и Vite** и **API TypeScript 6 (`@typescript/typescript6`) для ESLint**, пока `typescript-eslint` не поддержит API TS 7.1+. Алиас живёт в `devDependencies`; снять после выхода поддержки upstream.

## Последствия

- Меньше неожиданных поломок у операторов; переход на новый мажор Boot/JDK становится плановым минором ISPF с миграционной заметкой.
- Чуть более медленное принятие новых возможностей языка/фреймворка (обычно один патч-релиз).
- Существующие пины становятся видимым техдолгом с условием снятия, а не постоянной конфигурацией.
- `bootJar` `ispf-server` уменьшится после вынесения Parquet (отдельная задача).

## Связанные

- ADR-0022 матрица зрелости драйверов (lab ≠ field)
- [architecture](../architecture.md) § Stack
- Анализ кода 2026-09, находки F-02, F-07
