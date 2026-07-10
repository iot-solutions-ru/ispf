> **Language:** Canonical English. Russian edition: [ru/blueprints.md](../ru/blueprints.md).

# Blueprints plugin

Module `ispf-plugin-blueprint` — **blueprint** system (object structure templates). One engine, three kinds in the tree and API.

## Three model kinds (`BlueprintType`)

| Type | Catalog | Behavior |
|-----|---------|-----------|
| `RELATIVE` | `root.platform.relative-blueprints` | Optional mixins — variables/events/functions **merge into** existing object |
| `INSTANCE` | `root.platform.instance-types` | **Object type** template — create instances via instantiate |
| `ABSOLUTE` | `root.platform.absolute-blueprints` | Singleton blueprint — one live object in `root.platform.instances.*` |

**Intrinsic schemas** (1:1 with `ObjectType`: `DATA_SOURCE`, `SCHEDULE`, `DASHBOARD`, …) live in the registry for bootstrap but **do not appear** in the relative-blueprints catalog and **are not used** in `appliedBlueprintIds`. Structure is embedded in the instance via `*ObjectService.ensureStructure()`.

See [0011-model-type-semantics](decisions/0011-model-type-semantics.md).

### Object linkage

- `templateId` — primary model (INSTANCE/ABSOLUTE)
- `appliedBlueprintIds` — JSON array of all applied model ids (persisted)
- On merge name collision: **last wins + warning** (not error)

### Automatic RELATIVE apply

On `POST /objects` with `autoApplyRelativeBlueprints=true` (default) `BlueprintEngine.applyRelativeModels()` runs.

**Auto-apply conditions** (all required):

1. `BlueprintType.RELATIVE`, not system
2. `targetObjectType` matches object type
3. **`suitabilityExpression` (applicability condition, CEL) is not empty** and evaluates to `true`
4. Model not already in `appliedBlueprintIds`

**Empty CEL** → model **never** auto-applies. For unconditional mixin use explicit apply: `templateId` on create, `POST /api/v1/relative-blueprints/{id}/apply`, companion models in bundle.

**Explicit apply** (template/API): only `targetObjectType`; CEL optional — if set, must be `true`.

See [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

### Applicability condition (CEL)

Field `suitabilityExpression` in `BlueprintDefinition` — in Web Console: *Applicability condition (CEL)*.

| Value | Auto-apply | Explicit apply |
|----------|------------|-------------|
| empty | no | yes (by object type) |
| `true` | to all `targetObjectType` objects | yes |
| `self.flag.value == true` | only when true | yes, if CEL true |

Expression evaluated via `ExpressionEngine` with `self` = target object variables.

## Three-tier models

| Tier | Source | When in registry |
|---------|----------|------------------|
| **System-intrinsic** | `ModelBootstrap` | Always; not in relative-blueprints catalog |
| **Platform built-in** | `ModelBootstrap` | Always (`dashboard-v1`, `alert-rule-v1`, …) |
| **Fixtures** | `FixtureModelBootstrap` | Only `ispf.bootstrap.fixtures-enabled=true` |
| **Solution** | `bundle.json` → `models[]` | On bundle deploy |

**System intrinsic** (schema embedded in instance, no `appliedBlueprintIds`): `data-source-v1`, `schedule-v1`, `sql-binding-v1`, `migration-v1`, `alert-rule-v1`, `correlator-v1`, `dashboard-v1`, `report-v1`, `workflow-v1`.

**Fixtures** (demo/lab, not core): `device-driver-v1`, `mqtt-gateway-v1`, `mqtt-sensor-v1`, `base-sensor-v1`, `vendor-sensor-ext-v1`, `snmp-agent-v1`. Config: `ispf.bootstrap.fixtures-enabled` (default `true`).

See [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

## API

| Method | Path | Description |
|--------|------|----------|
| GET/POST/… | `/api/v1/relative-blueprints` | RELATIVE facades |
| GET/POST/… | `/api/v1/instance-types` | INSTANCE facades (+ `?platformType=` for create dialog) |
| GET/POST/… | `/api/v1/absolute-blueprints` | ABSOLUTE facades |
| GET/POST/… | `/api/v1/blueprints` | Legacy unified API (compatibility) |

## Model composition (`BlueprintDefinition`)

- Variables (`ModelVariableDefinition`) — schema, default, group, readable/writable
- Events (`EventDescriptor`)
- Functions (`FunctionDescriptor`)
- Binding rules (`ModelBindingRule`) — see [bindings](bindings.md)
- Metadata: name, description, `ObjectType`, `BlueprintType`

## Engine

| Class | Purpose |
|-------|------------|
| `ModelRegistry` | In-memory model catalog |
| `BlueprintEngine` | CRUD, apply, instantiate, fromObject, `applyRelativeModels` (CEL-gated) |
| `ModelBootstrap` | Platform built-in + system-intrinsic models |
| `FixtureModelBootstrap` | Demo/lab fixture models (`fixtures-enabled`) |
| `ModelPersistenceService` | Persist user models in `blueprint_definitions` (REQ-PF-07) |

User models (non-`builtin`) load from DB on server start after `ensureBuiltInModels()`.

## API (unified)

| Method | Path | Description |
|--------|------|----------|
| GET | `/api/v1/blueprints` | List |
| GET | `/api/v1/blueprints/{id}` | By ID |
| GET | `/api/v1/blueprints/by-name/{name}` | By name |
| POST | `/api/v1/blueprints` | Create |
| PUT | `/api/v1/blueprints/{id}` | Update |
| DELETE | `/api/v1/blueprints/{id}` | Delete |
| POST | `/api/v1/blueprints/{id}/apply?objectPath=` | Apply to object |
| POST | `/api/v1/blueprints/{id}/instantiate` | Create instance |
| POST | `/api/v1/blueprints/from-object` | Export model from object |
| GET | `/api/v1/blueprints/attachments` | Model↔type attachments |

Access: **admin**. See [api](api.md).

## Built-in and fixture models

### Platform built-in (always)

| Model | Type | Purpose |
|--------|-----|------------|
| `dashboard-v1` | intrinsic RELATIVE | Layout HMI |
| `workflow-v1` | intrinsic RELATIVE | BPMN workflow |
| `report-v1` | intrinsic RELATIVE | SQL report |
| `alert-rule-v1` | intrinsic RELATIVE | CEL alert rule |
| `correlator-v1` | intrinsic RELATIVE | Event correlator |
| `data-source-v1`, `schedule-v1`, … | intrinsic RELATIVE | System object type schemas |

### Fixtures (`ispf.bootstrap.fixtures-enabled=true`)

Demo/lab models **not** included in core `ModelBootstrap`. Registered by `FixtureModelBootstrap` on start (when fixtures enabled).

#### mqtt-sensor-v1

Demo MQTT temperature sensor. Applied to `demo-sensor-01` via `templateId`, not auto-apply (default CEL empty).

| Variable | Group | Description |
|------------|--------|----------|
| `temperature` | telemetry | value, unit (history) |
| `threshold` | config | Threshold |
| `alarmActive`, `alarmAcknowledged` | status | Alarm state |
| `temperaturePercent` | telemetry | Binding |

Event: `thresholdExceeded`. Function: `acknowledgeAlarm`.

#### mqtt-gateway-v1

MQTT ingress gateway — one broker, routes `lastIngress` to child sensors via `dispatchTelemetry`. See [0017-telemetry-ingest-pipeline](decisions/0017-telemetry-ingest-pipeline.md).

#### device-driver-v1

RELATIVE mixin with `driver` group variables — for demo/lab and explicit apply. **Not** used for auto-apply on DEVICE create.

On production path driver schema is provisioned via `provisionDriver()` without relative mixin (see [drivers](drivers.md)).

#### snmp-agent-v1

SNMP device (MIB-II + HOST-RESOURCES). Demo: `root.platform.devices.snmp-localhost`.

| Variable | OID | Description |
|------------|-----|----------|
| `sysName` | 1.3.6.1.2.1.1.5.0 | Host name |
| `sysDescr` | 1.3.6.1.2.1.1.1.0 | System / OS description |
| `sysUpTime` | 1.3.6.1.2.1.1.3.0 | Uptime (TimeTicks) |
| `sysLocation` | 1.3.6.1.2.1.1.6.0 | Location |
| `sysContact` | 1.3.6.1.2.1.1.4.0 | Contact |
| `hrMemorySize` | 1.3.6.1.2.1.25.2.2.0 | RAM size (KB) |
| `hrSystemProcesses` | 1.3.6.1.2.1.25.1.6.0 | Process count |
| `hrSystemNumUsers` | 1.3.6.1.2.1.25.1.5.0 | User count |
| `ifNumber` | 1.3.6.1.2.1.2.1.0 | Network interface count |
| `ifInOctets` | 1.3.6.1.2.1.2.2.1.10.1 | Ingress octets (interface #1) |
| `ifOutOctets` | 1.3.6.1.2.1.2.2.1.16.1 | Egress octets (interface #1) |
| `hrProcessorLoad` | 1.3.6.1.2.1.25.3.3.1.2.1 | CPU load % (core #1) |

Used by dashboard `root.platform.dashboards.snmp-host-monitoring`.

#### base-sensor-v1 / vendor-sensor-ext-v1

INSTANCE + RELATIVE extension family for vendor model inheritance (see `ModelUpgradeApiTest`).

## Example: create instance

```http
POST /api/v1/blueprints/{id}/instantiate
Content-Type: application/json

{
  "parentPath": "root.platform.devices",
  "name": "sensor-02",
  "parameters": {}
}
```

## Related documents

- [object-model](object-model.md) — variables, DataRecord
- [dashboards](dashboards.md) — dashboard-v1 layout
- [workflows](workflows.md) — workflow-v1
- [drivers](drivers.md) — driver variables, provisioning
- [decisions/0018-fixture-models-and-cel-applicability.md](decisions/0018-fixture-models-and-cel-applicability.md) — fixtures + CEL auto-apply
