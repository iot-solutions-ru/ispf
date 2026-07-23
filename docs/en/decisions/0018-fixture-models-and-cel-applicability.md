# ADR-0018: Fixture models and CEL applicability for MIXIN

> **Note (2026):** Fixture models and API were renamed to blueprint (`FixtureBlueprint*`, `/api/v1/mixin-blueprints`). See [blueprints](../blueprints.md).

## Status

Accepted (2026-06-25)

## Context

Three overlapping problems:

1. **Demo/lab models in core** — `mqtt-sensor-v1`, `mqtt-gateway-v1`, `device-driver-v1`, `snmp-agent-v1`, and the `base-sensor-v1` family were registered as built-in platform models and auto-applied when creating any `DEVICE`.
2. **`device-driver-v1` as a MIXIN** — with `autoApplyMixinBlueprints=true`, the driver schema was applied before `provisionDriver(mqtt)` with default `driverId=virtual`, after which runtime rejected driver changes.
3. **Empty CEL = «matches all»** — `suitabilityExpression` (UI: *Applicability condition*) with an empty value was treated as an unconditional match on `targetObjectType`, making auto-apply unpredictable.

Standard platform artifacts (dashboard, workflow, data-source schema) must not be optional MIXIN blueprints. Demo and lab templates must not live in the core registry without explicit enablement.

## Decision

### 1. Three model tiers

| Tier | Registration | mixin-blueprints catalog | Auto-apply MIXIN |
|------|-------------|----------------------------|---------------------|
| **System-intrinsic** | `ModelBootstrap` + `parameters.systemIntrinsic=true` | No | No — structure embedded via `SystemObjectStructureService` |
| **Platform built-in** | `ModelBootstrap.ensureBuiltInModels()` | Yes (if not intrinsic) | Only with non-empty CEL (see §2) |
| **Fixtures** | `FixtureModelBootstrap` when `ispf.bootstrap.fixtures-enabled=true` | Yes | Only with non-empty CEL; otherwise explicit `templateId` / API apply |

**System-intrinsic** (always): `data-source-v1`, `schedule-v1`, `sql-binding-v1`, `migration-v1`, `alert-rule-v1`, `correlator-v1`, `dashboard-v1`, `report-v1`, `workflow-v1`.

**Fixtures** (optional, default `fixtures-enabled=true`):

| Model | Purpose |
|-------|---------|
| `device-driver-v1` | MIXIN: variables in group `driver` (demo/lab) |
| `mqtt-gateway-v1` | MQTT ingress gateway + `dispatchTelemetry` |
| `mqtt-sensor-v1` | Demo MQTT temperature sensor |
| `base-sensor-v1` | Sensor family blueprint (INSTANCE) |
| `vendor-sensor-ext-v1` | base-sensor extension (MIXIN) |
| `snmp-agent-v1` | SNMP demo device |

Solution bundles ship their own models via `models[]` in `bundle.json` (see `examples/lab-mqtt-temperature/`).

### 2. CEL applicability (`suitabilityExpression`)

Field `BlueprintDefinition.suitabilityExpression` — **Applicability condition (CEL)** in the model editor.

| Apply path | Empty CEL | Non-empty CEL |
|------------|-----------|---------------|
| **Auto-apply** (`applyMixinBlueprints` on `POST /objects`, `autoApplyMixinBlueprints=true`) | **Not applied** | Applied when CEL → `true` and `targetObjectType` matches |
| **Explicit apply** (`templateId`, `POST /mixin-blueprints/{id}/apply`, companion-blueprints) | Allowed (only `targetObjectType` checked) | CEL must evaluate to `true` |

Implementation: `BlueprintEngine.isSuitableForAutoApply()` — empty CEL → `false`; `assertSuitable()` for manual apply — CEL optional.

Example CEL for auto-apply:

```cel
true
self.templateId == "mqtt-sensor-v1"
self.driverId.value == "mqtt"
```

### 3. Driver schema on DEVICE (not MIXIN auto-apply)

Variables `driverId`, `driverStatus`, `driverConfigJson`, … on **any** `DEVICE` during provisioning are embedded via `DeviceProvisioningService` → `SystemObjectStructureService.ensureDeviceDriverStructure()` from blueprint `FixtureBlueprintDefinitions.buildDeviceDriverModel()` (**without** a catalog entry and **without** `appliedBlueprintIds`).

This is separate from MIXIN `device-driver-v1` (fixture for demo/lab and explicit apply).

Order on `POST /objects` (DEVICE + `driverId`):

1. Create node
2. `applyTemplate(templateId)` — if set
3. `applyMixinBlueprintsWithRules` — only models with non-empty CEL
4. `provisionDriver(driverId)` — embed driver schema + configure runtime

### 4. Fixture configuration

```yaml
ispf:
  bootstrap:
    fixtures-enabled: ${ISPF_BOOTSTRAP_FIXTURES_ENABLED:true}
```

When `fixtures-enabled=false`: demo nodes in `PlatformBootstrap.initializeFixtures()` are not created; fixture models are not registered; driver provisioning on DEVICE still works (embedded schema).

## Consequences

- `device-driver-v1`, `mqtt-gateway-v1` removed from `ModelBootstrap` and `SystemIntrinsicModels`.
- Code: `FixtureModelBootstrap`, `FixtureBlueprintDefinitions`, updated `BlueprintEngine`.
- Existing MIXIN models without CEL no longer auto-apply — set an expression in the editor for auto-apply.
- Documentation: [blueprints](../blueprints.md), [drivers](../drivers.md).

## Related

- [0011-model-type-semantics](0011-model-type-semantics.md) — three model kinds
- [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md) — `mqtt-gateway-v1` ingest
- [blueprints](../blueprints.md)
- [drivers](../drivers.md)
