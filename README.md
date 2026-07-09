# IoT Solutions Platform Framework (ISPF)

Modern IoT/SCADA platform on a cloud-native 2026 stack.

**Status:** active development on `main` — full admin + operator HMI, application platform (REQ-PF), production-ready local/dev profiles.

## Core idea

**Business logic lives on the platform** — in models, variables, events, functions, and workflows on the object tree. The core provides generic engines; solutions are declarative configuration (including bundle deploy). See [architecture](docs/en/architecture.md).

ISPF is built around a **hierarchical object tree** with typed variables, events, functions, and computed bindings:

| Concept | Implementation |
| -------- | -------------- |
| Object tree | `ObjectTree` / REST API, drag-and-drop sibling order |
| Typed data | `DataRecord` + `DataSchema` |
| Computed bindings | Google CEL (`ispf-expression`) |
| Device drivers | `DeviceDriver` SPI — **58 driverId** (Modbus, OPC UA, SNMP, MQTT, JDBC, …) |
| HMI | Dashboard builder — 14 widget types, variable history on charts |
| Automation | BPMN workflow, alert rules and correlators as **tree nodes** |
| Application layer | REQ-PF: bundle deploy, functions, reports, BFF, scheduler |
| Storage | PostgreSQL + TimescaleDB (prod), H2 (local/test); Redis, NATS optional |
| UI | Spring Boot 4.0 + Java 25 + React 19 (Vite) |
| Integration | REST + WebSocket |

## Documentation

**Start here:**

| | |
| --- | --- |
| **English (canonical)** | [docs/en/readme.md](docs/en/readme.md) |
| **Русский** | [docs/ru/readme.md](docs/ru/readme.md) |
| **Doc hub** | [docs/README.md](docs/README.md) |

**Essential links:**

| Topic | English | Русский |
| ----- | ------- | ------- |
| Product overview | [product.md](docs/en/product.md) | [product.md](docs/ru/product.md) |
| Getting started | [getting-started.md](docs/en/getting-started.md) | [getting-started.md](docs/ru/getting-started.md) |
| Architecture | [architecture.md](docs/en/architecture.md) | [architecture.md](docs/ru/architecture.md) |
| Operator guide | [operator-guide.md](docs/en/operator-guide.md) | [operator-guide.md](docs/ru/operator-guide.md) |
| Solution developer | [solution-developer-guide.md](docs/en/solution-developer-guide.md) | [solution-developer-guide.md](docs/ru/solution-developer-guide.md) |
| REST API | [api.md](docs/en/api.md) | [api.md](docs/ru/api.md) |
| Drivers | [drivers.md](docs/en/drivers.md) | [drivers.md](docs/ru/drivers.md) |
| Roadmap | [roadmap.md](docs/en/roadmap.md) | [roadmap.md](docs/ru/roadmap.md) |
| ADR index | [decisions/readme.md](docs/en/decisions/readme.md) | [decisions/readme.md](docs/ru/decisions/readme.md) |
| Glossary | [glossary.md](docs/en/glossary.md) | [glossary.md](docs/ru/glossary.md) |

## Repository layout

```
iot-solutions-platform-framework/
├── packages/
│   ├── ispf-core/              # Domain: objects, DataRecord, ObjectType
│   ├── ispf-expression/        # CEL engine
│   ├── ispf-driver-api/        # Driver SPI
│   ├── ispf-driver-*/          # Protocol drivers
│   ├── ispf-plugin-blueprint/  # Models plugin
│   ├── ispf-plugin-workflow/   # BPMN engine
│   └── ispf-server/            # Spring Boot API + JPA + Flyway
├── apps/web-console/           # React console (Explorer, System, Operator)
├── examples/                   # demo-app, mes-reference, lab-training, …
├── tools/license-builder/      # RSA keys + commercial bundle signing
├── docs/
│   ├── en/                     # Canonical English docs
│   └── ru/                     # Russian docs
├── deploy/
└── docker-compose.yml
```

## Quick start

```bash
# Infrastructure (optional for dev)
docker compose up -d

# API (local — no OAuth, H2)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# Web Console
cd apps/web-console && npm install && npm run dev
```

| URL | Purpose |
| --- | ------- |
| http://localhost:8080/api/v1/info | Version and capabilities |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:5173 | Web Console (admin) |
| http://localhost:5173?mode=operator | Operator HMI |

Details: [docs/en/getting-started.md](docs/en/getting-started.md)

## Demo objects

| Path | Purpose |
| ---- | ------- |
| `root.platform.devices.demo-sensor-01` | Virtual sensor + alarm |
| `root.platform.devices.snmp-localhost` | SNMP agent |
| `root.platform.dashboards.demo-sensor` | HMI dashboard |
| `root.platform.workflows.demo-alarm-handler` | BPMN demo |
| `root.platform.alert-rules.temperature-threshold-exceeded` | CEL alert rule |
| `root.platform.correlators.alarm-handler-on-threshold-event` | Correlator → workflow |

## RBAC

| Profile | Mechanism |
| ------- | --------- |
| `local` | `X-ISPF-Role: admin\|operator` |
| `dev` | JWT Keycloak, realm `ispf` |

See [security](docs/en/security.md).

## Tests

```bash
./gradlew test
```

## License

**[GNU Affero General Public License v3.0](LICENSE)** — platform (`ispf-server`, `web-console`, core packages).

Commercial Enterprise license: [LICENSE-COMMERCIAL.md](LICENSE-COMMERCIAL.md), [commercial-licensing](docs/en/commercial-licensing.md).

Device drivers ship as separate **driver packs**. Application bundles use customer EULA.

More: [license](docs/en/license.md), [plugins](docs/en/plugins.md), [NOTICE](NOTICE).
