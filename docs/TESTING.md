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

JUnit 5, Java 21.

## Профиль test

`application-test.yml`:

- H2 in-memory
- `ispf.security.rbac-enabled: false`
- MQTT/NATS disabled
- Flyway migrations applied

## Уровни тестов

### Unit (packages)

| Модуль | Примеры |
|--------|---------|
| ispf-core | `ObjectTreeTest`, `DataRecordTest` |
| ispf-expression | `BindingEvaluatorTest`, `ExpressionEngineTest` |
| ispf-plugin-model | `ModelEngineTest` |
| ispf-plugin-workflow | `BpmnParserTest`, `WorkflowEngineV2/V3Test` |
| ispf-driver-modbus | `ModbusPointTest` |
| ispf-driver-snmp | `SnmpPointTest` |

### Integration (ispf-server)

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
| `ModelControllerTest` | Models API |
| `OperatorAccessTest` | RBAC operator vs admin |

### Сценарии demo-стенда

1. Virtual driver → temperature updates
2. Binding `alarmActive` при temp > threshold
3. `acknowledgeAlarm` function
4. Alert rule → `thresholdExceeded` event
5. Correlator → workflow run
6. User task в work queue

## Smoke (ручной)

После `bootRun --spring.profiles.active=local`:

```bash
curl http://localhost:8080/api/v1/info
curl -H "X-ISPF-Role: admin" http://localhost:8080/api/v1/objects
```

Web Console: открыть `demo-sensor` dashboard, убедиться в live-значениях.

## Web Console

```bash
cd apps/web-console
npm run build   # tsc + vite build
```

TypeScript strict check в CI-equivalent локально.

## CI (рекомендация)

```bash
./gradlew test
cd apps/web-console && npm ci && npm run build
```

## Эталон нефтебазы

Smoke-скрипт на ветке `feature/oil-terminal-reference`:

`examples/oil-terminal/oil-terminal-smoke.ps1`

Не входит в `main`.
