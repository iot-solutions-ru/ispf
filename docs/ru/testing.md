> **Язык:** русская версия (вычитка). Канонический английский: [en/testing.md](../en/testing.md).

# Тестирование

> **Статус:** Stable — Unit, integration. Теги: [doc-status](../en/doc-status.md).

## Запуск

```bash
# Все модули
./gradlew test

# Только server
./gradlew :packages:ispf-server:test

# Один класс
./gradlew :packages:ispf-server:test --tests "com.ispf.server.api.DashboardApiTest"
```

JUnit 5, Java 25, Spring Boot 4.

В бэкэнд-тестах используются модульные стартеры Boot 4 (`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`). JSON использует Джексон 3 (`tools.jackson`); `@JsonProperty` / `@JsonCreator` оставайтесь на `com.fasterxml.jackson.annotation`.

## Тест профиля

`application-test.yml`:

- H2 в памяти
- `ispf.security.rbac-enabled: false`
- MQTT/NATS отключен.
- Применены миграции пролетных путей.

## Уровни испытаний

### Единица (пакеты)

| Модуль | Примеры |
|--------|---------|
| ispf-core | `ObjectTreeTest`, `DataRecordTest` |
| ispf-expression | `BindingEvaluatorTest` (expression eval), `ExpressionEngineTest` |
| ispf-plugin-blueprint | `BlueprintEngineTest` |
| ispf-plugin-workflow | `BpmnParserTest`, `WorkflowEngineV2/V3Test` |
| ispf-driver-modbus | `ModbusPointTest` |
| ispf-driver-snmp | `SnmpPointTest` |

### Интеграция (ispf-сервер)

`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`:

| Класс | Область |
|-------|---------|
| `IspfServerApplicationTests` | Context load |
| `MesPlatformApiTest` | Objects, variables, demo sensor |
| `DashboardApiTest` | Dashboard CRUD |
| `WorkflowApiTest` | BPMN save, run |
| `WorkQueueApiTest` | Claim/complete |
| `AlertRuleApiTest` | Alert rules CRUD |
| `EventCorrelatorApiTest` | Correlators CRUD |
| `BlueprintControllerTest` | Blueprints API |
| `OperatorAccessTest` | RBAC operator vs admin |
| `ApplicationPlatformApiTest` | REQ-PF: applications, bundle deploy, BFF invoke |

### Сценарии демо-стенда

1. Виртуальный драйвер → обновления температуры
2. Привязка `alarmActive` при температуре > порога
3. Функция `acknowledgeAlarm`
4. Правило оповещения → `thresholdExceeded` событие
5. Коррелятор → запуск рабочего процесса
6. Задача пользователя в рабочей очереди

## Дым (ручной)

После `bootRun --spring.profiles.active=local`:

```bash
curl http://localhost:8080/api/v1/info
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/objects
```

(`X-ISPF-Role` **выключен по умолчанию**; не использовать в smoke, пока не включён `ispf.security.local-role-header-enabled=true`.)

Web Console: открыть `demo-sensor` dashboard, убедиться в live-значениях.

## Веб-консоль

```bash
cd apps/web-console
npm test        # vitest (routing, driver maturity)
npm run build   # tsc + vite build
```

Строгая проверка TypeScript в локальном эквиваленте CI.

## Нагрузка/пропускная способность (прод VPS)

Сценарии HTTP и внутреннего автоматического нагрузочного тестирования, базовые метрики и зонд дашборда — **[load-testing](load-testing.md)**.

```bash
python deploy/events-load-test.py --base-url ${ISPF_BASE_URL:-https://ispf.example.invalid}
python deploy/events-internal-load-test.py --skip-monitor-setup --condition-expr-file deploy/loadtest-sinewave-condition.txt
```

## Кластерные испытания (БЛ-137/138 + Wave 6)

Владение драйвером JDBC и аварийное переключение между репликами:

```bash
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.driver.DriverOwnershipServiceTest \
  --tests com.ispf.server.driver.ClusterFailoverIntegrationTest
```

Compose smoke (Docker, несколько реплик):

```bash
bash deploy/cluster-quickstart.sh          # build + up + smoke
ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS=1 \
  bash deploy/cluster-smoke-test.sh --config-sync --live-var-lag
python deploy/cluster-scale-load-test.py   # 1 vs 3 replica throughput (floor 1.8×)
```

CI: [`.github/workflows/cluster-load-test.yml`](../../.github/workflows/cluster-load-test.yml) — JDBC ownership (еженедельно) + compose smoke/scale (`workflow_dispatch`).

Chaos / soak под нагрузкой (журнал REAL vs PARTIAL): **[cluster-chaos-soak-runbook](cluster-chaos-soak-runbook.md)**. CI **не** доказывает multi-hour soak и kill-owner под sustained ingress.

## Гейт покрытия (JaCoCo)

Backend-джоб pr-fast падает, если покрытие модуля опускается ниже его порога. Пороги
(LINE / BRANCH covered ratio) заданы в `coverageFloors` в корневом `build.gradle.kts`
и работают как «трещотка»: поднимайте порог, когда покрытие растёт, и не понижайте молча.

```bash
./gradlew testPrFast -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev
./gradlew coverageVerify   # гейт
./gradlew coverageReport   # HTML/XML в <module>/build/reports/jacoco/test/
```

Модули без порога (драйверы, аналитика, AI-провайдеры) получают только отчёт.
## Статический анализ (Error Prone)

Каждый `javac` в Gradle-сборке идёт через [Error Prone](https://errorprone.info/) (`net.ltgt.errorprone`,
`error_prone_core` — версии закреплены в корневом `build.gradle.kts`). Паттерны уровня **ERROR** валят
компиляцию, отдельной задачи нет: `./gradlew compileJava compileTestJava` (или любой `test`) и есть гейт.

- Поднято до ERROR после того, как sweep 2026-09 исправил все вхождения: `DefaultCharset`, `StreamResourceLeak`,
  `NonAtomicVolatileUpdate`, `OrphanedFormatString`, `ArgumentSelectionDefectChecker`, `AlreadyChecked`,
  `DuplicateBranches`, `MissingOverride`. Понижать нельзя — правьте код или ставьте точечный
  `@SuppressWarnings("<Pattern>")` с причиной в одну строку рядом (принятая форма — `JmxDeviceDriver.connect`,
  `ApplicationSchemaSession`).
- Глобальный `disable(...)` — только для Javadoc-стилевых проверок (`MissingSummary`, `InvalidInlineTag`,
  `EscapedEntity`) и `AddressSelection`; у каждой записи есть причина в `build.gradle.kts`.
- Оставшиеся WARNING видны в логе компиляции; поднимать паттерн до ERROR — только в том же PR, где исправлены
  все его вхождения.
- Локально без чекера для быстрой итерации: `./gradlew ... -Pispf.errorprone=false`. CI этот флаг не ставит.

## Гейт уязвимостей зависимостей (nightly)

Job `dependency-vulnerabilities` в `.github/workflows/nightly.yml` собирает один CycloneDX SBOM по
`runtimeClasspath` всех модулей (`./gradlew cyclonedxBom` → `build/reports/cyclonedx/bom.json`) и сканирует
его Trivy:

- **CRITICAL / HIGH** с доступным фиксом валят ночь; **MEDIUM / LOW** — только в отчёте.
- Полная таблица по всем severity и сам SBOM выкладываются артефактом `dependency-vulnerabilities-<run>`
  (30 дней).
- Находка закрывается апгрейдом прямой зависимости, либо — если она транзитивная и владелец отстаёт —
  `resolutionStrategy.force` / override BOM в `build.gradle.kts` **с записью в реестре пинов ADR-0059**
  (CVE и условие снятия пина). Не подавлять через `.trivyignore`.
- Dependabot (`.github/dependabot.yml`) двигает прямые версии; nightly-скан — страховка от того, чего Dependabot
  не видит (транзитивные члены BOM).

Локально: `./gradlew cyclonedxBom -Pispf.errorprone=false && trivy sbom build/reports/cyclonedx/bom.json`.

## CI (рекомендация)

```bash
./gradlew test          # компиляция с Error Prone; ERROR-паттерны падают здесь
cd apps/web-console && npm ci && npm test && npm run build
# nightly: ./gradlew cyclonedxBom + Trivy (гейт CRITICAL/HIGH)
```

## App bundle smoke (вне `main`)

E2E дым показывает результаты приложения в репозитории App Bundle, а не в фреймворке `main`.
