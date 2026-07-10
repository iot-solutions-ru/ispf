> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0021-haystack-semantic-overlay.md](../../en/decisions/0021-haystack-semantic-overlay.md).

# ADR-0021: Haystack semantic overlay

## Статус

Принято (30 июня 2026 г.)

## Контекст

ISPF моделирует runtime state через **дерево объектов** (dot-paths, `DataSchema`, RELATIVE/INSTANCE models, bindings, historian). Project Haystack и Brick Schema дают industry-standard semantics для BMS, energy и digital-twin интеграций, но в платформе **не было** Haystack/Brick layer в коде (только обсуждение, BL-56…62).

Требования от app teams и integrators:

- Экспортировать equip/point semantics во внешние Haystack tools без замены ISPF paths.
- Optional tagging на devices и telemetry variables; дерево остаётся authoritative для bindings, ACL и history.
- Demand-driven Brick/RDF export позже (BIM / digital twin), не runtime replacement для object tree.

Связанные стандарты:

| Standard | Role |
| -------- | ---- |
| **Project Haystack** | Tag-based semantic model (`equip`, `point`, `sensor`, `temp`, `his`, …) |
| **Brick Schema** | Formal RDF/TTL class graph (`brick:Equipment`, `hasPoint`) |
| **OPC UA semantic model** | Companion specification nodes — полезная reference для mapping, не primary для ISPF |
| **ASHRAE 223P** | BACnet semantic tags — совпадают с Haystack tags для BACnet driver'ов |

## Решение

### 1. Object tree = source of truth

- Runtime paths (`root.platform.devices.*`), bindings, correlators, historian и ACL продолжают использовать ISPF dot-paths.
- Haystack tags — **optional metadata overlay**, хранимый как model-managed variables на devices (RELATIVE mixin).
- Haystack refs (`@site.equip`) **не** заменяют и не перенаправляют ISPF paths, пока явно не привязаны в future integration driver (BL-61).

### 2. Haystack overlay model (`haystack-metadata-v1`)

RELATIVE mixin на `DEVICE` object'ах с variables в группе `haystack`:

| Variable | Type | Purpose |
| -------- | ---- | ------- |
| `haystackTags` | STRING (JSON array) | Marker tag names, e.g. `["equip","lab","site"]` |
| `haystackRef` | STRING | Optional external Haystack id |
| `haystackKind` | STRING | Primary kind hint: `equip`, `point`, `site`, … |

Apply вручную через `POST /api/v1/relative-blueprints/{id}/apply` или platform bootstrap/demo fixtures.

**Demo:** `root.platform.devices.lab-userA-01` (virtual-lab fixture) поставляется с equip tags и sample export.

### 3. Haystack export API

`GET /api/v1/platform/haystack/export?rootPath=&includePoints=`

Возвращает JSON grid (`formatVersion`, `rows[]`), где каждая row включает:

- `id`, `path`, `dis` — ISPF identity и display name
- `tags` — marker map, derived из `haystackTags`
- `entityKind` — `equip` для tagged devices; `point` для historian-enabled variables при `includePoints=true`
- `curVal`, `unit` — current scalar из variable value (если есть)

Это **ISPF interchange format**, inspired Haystack JSON grids, а не full Zinc/HTTP Haystack server.

### 4. Out of scope (v1)

- Замена `root.platform.devices.*` paths на Haystack refs в runtime.
- Full Brick reasoner или RDF inference в platform core.
- Auto-discovery tags из BACnet/OPC UA без explicit mapping — out of scope; используйте extended `driverPointMappingsJson` (BL-59 Done).
- External Haystack poll driver (BL-61 Done).

### 5. Brick Schema (BL-60)

`brick-metadata-v1` RELATIVE mixin: `brickClass` URI на DEVICE. Export subset через `GET /api/v1/platform/brick/export` (`format=jsonld|turtle`). `brick:hasPoint` из driver point mappings (то же point discovery, что у Haystack export). Demo: `root.platform.devices.lab-userA-01`.

Out of scope: full Brick reasoner, runtime replacement object paths.

## Последствия


- Integrators могут экспортировать tagged subtrees без fork object model.
- RELATIVE mixin pattern совпадает с existing driver/lab mixin'ами ([0011-model-type-semantics](0011-model-type-semantics.md), [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md)).
- Чёткая граница: semantics — metadata; mechanisms остаются tree-first.

Risks:

- Point-level tags пока не first-class на каждой variable — используйте driver mapping JSON (BL-59 Done) или per-variable naming, пока нет variable annotations.
- Export format может потребовать Zinc compatibility layer, если нужен external FIN/SkySpark ingestion.

**Update (2026-06-30):** BL-57 Done — dedicated **Haystack** inspector tab с marker multiselect (`HaystackMetadataPanel`).

**Update (2026-06-30):** BL-61 Done — `ispf-driver-haystack` опрашивает external Haystack servers через HTTP JSON `read`.

**Update (2026-06-30):** BL-60 Done — `brick-metadata-v1`, `GET /platform/brick/export` (JSON-LD + Turtle), demo equip/point на `lab-userA-01`.

## Implementation map (BL-56…62)

| BL | Deliverable | Status |
| -- | ----------- | ------ |
| BL-56 | This ADR | Done |
| BL-57 | `haystack-metadata-v1` + lab demo + inspector tag editor | Done |
| BL-58 | `GET /api/v1/platform/haystack/export` | Done |
| BL-59 | Extended driver point mappings → Haystack export | Done |
| BL-62 | Tag search + dashboard auto-bind + agent tool | Done |
| BL-61 | External Haystack poll driver pack | Done |
| BL-60 | Brick Schema overlay + RDF export | Done |

## Связанные материалы

- [roadmap.md § Wave G (BL-56…62)](../roadmap.md#часть-e--полный-реестр-bl-01139)
- [BLUEPRINTS](../BLUEPRINTS.md) — RELATIVE mixins
- [drivers](../drivers.md) — point mappings (future Haystack tag normalization)
- [0002 Dogfooding gate](0002-dogfooding-gate.md)
