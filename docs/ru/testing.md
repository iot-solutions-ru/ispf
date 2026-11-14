> **Язык:** русская версия. Английский оригинал: [en/testing.md](../en/testing.md).

# Тестирование платформы

> **Статус:** Stable — модульные, интеграционные, регрессионные и статические проверки кода. Метки зрелости: [doc-status.md](doc-status.md).

Руководство по запуску автотестов и проверок качества для разработчиков, вносящих изменения в репозиторий ISPF.

---

## Быстрый запуск тестов

> **Совет:** для повседневной проверки перед отправкой PR используйте быстрый набор `testPrFast` (или скрипт `./tools/ci/pr-fast.sh` / `.\tools\ci\pr-fast.ps1`), а не полный `./gradlew test` по всем 162 модулям драйверов.

```bash
# Быстрый пред-PR прогон серверного ядра (как в CI pr-fast)
./gradlew testPrFast \
  -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev

# Один конкретный тестовый класс
./gradlew :packages:ispf-server:test --tests "com.ispf.server.api.DashboardApiTest" \
  -Dispf.test.skipLoad=true

# Сквозной регрессионный набор по всем основным движкам платформы (golden path)
./gradlew :packages:ispf-server:test --tests "com.ispf.server.regression.*" \
  -Dispf.test.skipLoad=true

# Все тесты модуля ispf-server (включая нагрузочные)
./gradlew :packages:ispf-server:test
```

Стек тестирования бэкенда: **JUnit 5**, **Java 25**, **Spring Boot 4** (`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`), сериализация Jackson 3 (`tools.jackson` с аннотациями `com.fasterxml.jackson.annotation`).

### Профиль `test` (`application-test.yml`)

- База данных H2 в оперативной памяти с накатом миграций **Flyway**;
- внешние брокеры MQTT и NATS по умолчанию отключены;
- для изолированных API-тестов ролевая проверка может отключаться или настраиваться в конкретном тесте.

---

## Уровни тестов

### 1. Модульные тесты библиотек (`packages/*`)

| Модуль | Примеры тестовых классов |
|--------|--------------------------|
| `ispf-core` | `ObjectTreeTest`, `DataRecordTest`, `BindingCascadeAnalyzerTest` |
| `ispf-expression` | `BindingEvaluatorTest`, `ExpressionEngineTest` |
| `ispf-plugin-blueprint` | `BlueprintEngineTest` |
| `ispf-plugin-workflow` | `BpmnParserTest`, `WorkflowEngineV2Test`, `WorkflowEngineV3Test` |
| `ispf-driver-*` | Loopback- и парсер-тесты протоколов (`ModbusTcpDeviceDriverTest`, `SnmpPointTest` и др.) |

### 2. Интеграционные и сквозные тесты (`packages/ispf-server`)

Запускаются с `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`:

| Класс | Что проверяет |
|-------|---------------|
| `IspfServerApplicationTests` | Подъём контекста Spring Boot |
| `DashboardApiTest`, `WorkflowApiTest`, `WorkQueueApiTest` | Работа с дашбордами, процессами BPMN и очередью задач оператора |
| `AlertRuleApiTest`, `EventCorrelatorApiTest` | Правила тревог, гистерезис и корреляторы событий |
| `BlueprintControllerTest`, `OperatorAccessTest` | Шаблоны объектов и разграничение прав `operator` / `admin` |
| `ApplicationPlatformApiTest` | Деплой пакетов `bundle.json`, миграции SQL и вызовы `/api/v1/bff/invoke` |
| `PlatformGoldenPathRegressionTest` | Сквозной сценарий по всем подсистемам: шаблоны (`SINGLETON` / `INSTANCE` / `MIXIN`) + `DEVICE`, валидация и деплой пакета, BFF, `sqlBinding`, CEL, архив историй, тревога → коррелятор → BPMN → очередь оператора |
| `PlatformChangeSetAndBackupRegressionTest` | Наборы изменений (`change-sets`), блокировки редактирования (`leases`), аудит и экспорт/импорт ветки дерева |
| `PlatformWebSocketLiveRegressionTest` | Живые подписки по WebSocket `/ws/objects` |
| `AdrHonestyGateTest`, `DriverProductionMatrixTest` | Автоматическая сверка таблиц готовности драйверов и ADR с реальным кодом |

### 3. Фронтенд веб-консоли (`apps/web-console`)

```bash
cd apps/web-console
npm run lint        # ESLint
npm test            # модульные тесты Vitest
npm run i18n:check  # полнота ключей локализации RU/EN
npm run build       # проверка типов TypeScript (tsc) + сборка Vite
```

---

## Ручная быстрая проверка запуска (`smoke`)

После локального запуска сервера (`--spring.profiles.active=local`):

```bash
curl http://localhost:8080/api/v1/info

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/objects
```

Откройте дашборд `demo-sensor` в браузере, запустите драйвер `demo-sensor-01` и убедитесь, что показания обновляются в реальном времени.

---

## Кластерные и нагрузочные испытания

- **Распределение драйверов и отказоустойчивость реплик:**
  ```bash
  ./gradlew :packages:ispf-server:test \
    --tests com.ispf.server.driver.DriverOwnershipServiceTest \
    --tests com.ispf.server.driver.ClusterFailoverIntegrationTest
  ```
- **Многоузловой стенд Docker Compose:** `bash deploy/cluster-quickstart.sh` и `bash deploy/cluster-smoke-test.sh --config-sync --live-var-lag` (регламент длительных испытаний — [cluster-chaos-soak-runbook](cluster-chaos-soak-runbook.md)).
- **Нагрузочные замеры пропускной способности:** методики и скрипты описаны в разделе [Нагрузочное тестирование](load-testing.md).

---

## Автоматические проверки качества кода в CI

### 1. Контроль покрытия тестами (JaCoCo)

В корневом `build.gradle.kts` заданы минимальные пороги покрытия строк и ветвлений (`coverageFloors`) по ключевым модулям. При падении покрытия ниже зафиксированного порога задача `./gradlew coverageVerify` завершается с ошибкой (отчёты формируются командой `./gradlew coverageReport`).

### 2. Статический анализ компилятора (Error Prone)

Компиляция Java подключена к анализатору [Error Prone](https://errorprone.info/). Критичные категории дефектов (`DefaultCharset`, `StreamResourceLeak`, `NonAtomicVolatileUpdate`, `OrphanedFormatString`, `ArgumentSelectionDefectChecker`, `AlreadyChecked`, `DuplicateBranches`, `MissingOverride`) переведены в статус ошибки компиляции (`ERROR`). Для ускоренной черновой сборки на локальной машине анализ можно временно пропустить флагом `-Pispf.errorprone=false` (в CI он всегда включён).

### 3. Проверка уязвимостей сторонних библиотек (CycloneDX + Trivy)

Ночной конвейер `.github/workflows/nightly.yml` собирает полный перечень зависимостей (`./gradlew cyclonedxBom` → `build/reports/cyclonedx/bom.json`) и проверяет его сканером Trivy: уязвимости уровней `CRITICAL` и `HIGH` с выпущенным исправлением блокируют сборку ([ADR-0059](decisions/0059-toolchain-version-policy.md)).
