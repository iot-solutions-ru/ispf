> **Language:** Canonical English. Russian edition: [ru/testing.md](../ru/testing.md).

# Testing

## Running tests

```bash
# All modules
./gradlew test

# Server only
./gradlew :packages:ispf-server:test

# Single class
./gradlew :packages:ispf-server:test --tests "com.ispf.server.api.DashboardApiTest"
```

JUnit 5, Java 25, Spring Boot 4.

Backend tests use modular Boot 4 starters (`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`). JSON uses Jackson 3 (`tools.jackson`); `@JsonProperty` / `@JsonCreator` stay on `com.fasterxml.jackson.annotation`.

## Test profile

`application-test.yml`:

- H2 in-memory
- `ispf.security.rbac-enabled: false`
- MQTT/NATS disabled
- Flyway migrations applied

## Test levels

### Unit (packages)

| Module | Examples |
|--------|---------|
| ispf-core | `ObjectTreeTest`, `DataRecordTest` |
| ispf-expression | `BindingEvaluatorTest` (expression eval), `ExpressionEngineTest` |
| ispf-plugin-blueprint | `BlueprintEngineTest` |
| ispf-plugin-workflow | `BpmnParserTest`, `WorkflowEngineV2/V3Test` |
| ispf-driver-modbus | `ModbusPointTest` |
| ispf-driver-snmp | `SnmpPointTest` |

### Integration (ispf-server)

`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`:

| Class | Area |
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

### Demo stand scenarios

1. Virtual driver → temperature updates
2. Binding `alarmActive` when temp > threshold
3. `acknowledgeAlarm` function
4. Alert rule → `thresholdExceeded` event
5. Correlator → workflow run
6. User task in work queue

## Smoke (manual)

After `bootRun --spring.profiles.active=local`:

```bash
curl http://localhost:8080/api/v1/info
curl -H "X-ISPF-Role: admin" http://localhost:8080/api/v1/objects
```

Web Console: open `demo-sensor` dashboard, verify live values.

## Web Console

```bash
cd apps/web-console
npm test        # vitest (routing, driver maturity)
npm run build   # tsc + vite build
```

TypeScript strict check in CI-equivalent locally.

## Load / throughput (prod VPS)

HTTP scenarios and internal automation load test, baseline metrics, and dashboard probe — **[load-testing.md](load-testing.md)**.

```bash
python deploy/events-load-test.py --base-url https://ispf.iot-solutions.ru
python deploy/events-internal-load-test.py --skip-monitor-setup --condition-expr-file deploy/loadtest-sinewave-condition.txt
```

## Cluster tests (BL-137/138)

JDBC driver ownership and failover between replicas:

```bash
./gradlew :packages:ispf-server:test \
  --tests com.ispf.server.driver.DriverOwnershipServiceTest \
  --tests com.ispf.server.driver.ClusterFailoverIntegrationTest
```

Multi-replica compose smoke (Docker):

```bash
bash deploy/cluster-quickstart.sh          # build + up + smoke
bash deploy/cluster-smoke-test.sh          # round-robin, failover, driver reclaim
python deploy/cluster-scale-load-test.py   # 1 vs 3 replica throughput (floor 1.8×)
```

CI: workflow [`.github/workflows/cluster-load-test.yml`](../.github/workflows/cluster-load-test.yml) — JDBC ownership (weekly) + compose smoke/scale (`workflow_dispatch`).

## CI (recommended)

```bash
./gradlew test
cd apps/web-console && npm ci && npm test && npm run build
```

## App bundle smoke (outside `main`)

E2E smoke for a specific application runs in the app bundle repository, not in framework `main`.
