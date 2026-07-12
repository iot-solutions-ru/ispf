> **Язык:** русская версия (вычитка). Канонический английский: [en/testing.md](../en/testing.md).

# Тестирование

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
curl -H "X-ISPF-Role: admin" http://localhost:8080/api/v1/objects
```

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

## Кассетные испытания (БЛ-137/138)

Владение драйвером JDBC и аварийное переключение между репликами:

```bash
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.driver.DriverOwnershipServiceTest \
  --tests com.ispf.server.driver.ClusterFailoverIntegrationTest
```

Составление дыма с несколькими репликами (Docker):

```bash
bash deploy/cluster-quickstart.sh          # build + up + smoke
bash deploy/cluster-smoke-test.sh          # round-robin, failover, driver reclaim
python deploy/cluster-scale-load-test.py   # 1 vs 3 replica throughput (floor 1.8×)
```

CI: рабочий процесс [`.github/workflows/cluster-load-test.yml`](../.github/workflows/cluster-load-test.yml) — владение JDBC (еженедельно) + создание дыма/масштабирования (`workflow_dispatch`).

## CI (рекомендация)

```bash
./gradlew test
cd apps/web-console && npm ci && npm test && npm run build
```

## App bundle smoke (вне `main`)

E2E дым показывает результаты приложения в репозитории App Bundle, а не в фреймворке `main`.
