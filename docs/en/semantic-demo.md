# Semantic demo walkthrough (S23-04 / BL-105)

Goal: first semantic dashboard from Haystack query in **≤5 minutes** (acceleration KPI S23-04).

## Prerequisites

- ISPF server + Web Console running (`local` profile or lab fixtures).
- Operator role (semantic APIs require authenticated session in prod; test profile allows MockMvc/CI).

## Steps (≈5 min)

### 1. Apply mixins (new device)

In **Explorer** → device → **Blueprints**, apply:

| Mixin | Purpose |
| ----- | ------- |
| `haystack-metadata-v1` | `haystackTags`, `haystackRef`, `haystackKind` |
| `brick-metadata-v1` | `brickClass` URI |

Lab shortcut: `root.platform.devices.lab-userA-01` already has both mixins from bootstrap.

```http
POST /api/v1/mixin-blueprints/{blueprintId}/apply?objectPath=root.platform.devices.my-device
```

### 2. Set Haystack tags

**Haystack** inspector tab → marker multiselect: `equip`, `lab`, `site` on device; extend `driverPointMappingsJson` with point tags (`point`, `sensor`, `temp`) and units.

Demo device tags `sineWave` with `temp` → °C.

### 3. Infer and set Brick class

**Brick** tab → **Infer** (calls `GET /api/v1/platform/brick/infer?objectPath=…`) → apply suggestion → save `brickClass` (demo: `brick:Sensor`).

Tag-only inference (no object):

```http
GET /api/v1/platform/brick/infer?tags=equip,meter,energy&haystackKind=equip
```

### 4. Haystack query

```http
GET /api/v1/platform/haystack/query?filter=point%20and%20temp&rootPath=root.platform.devices.lab-userA-01&entityKind=point
```

Export grid (same tag index):

```http
GET /api/v1/platform/haystack/export?rootPath=root.platform.devices.lab-userA-01&includePoints=true
```

### 5. Dashboard auto-bind

Dashboard builder → **Haystack bind** dialog → filter `point and temp` → bind value/chart widgets to matched points (BL-103).

### 6. Semantic export (optional)

Platform → **Semantic export**: Haystack JSON + Brick JSON-LD/Turtle for a subtree.

```http
GET /api/v1/platform/brick/export?rootPath=root.platform.devices.lab-userA-01&format=jsonld&includePoints=true
```

Brick graph uses `ispf:path` and `urn:ispf:platform:…` IRIs aligned with `BrickExportService.entityIri`; `temp` points map to `brick:Temperature_Sensor`.

## API roundtrip (CI / S23-03)

`SemanticRoundtripIntegrationTest` verifies for `lab-userA-01`:

- Haystack export ↔ `haystack/query` filter results
- Brick JSON-LD `@graph` nodes for every tagged equip (when `brickClass` set) and `temp` points
- `@id` / `ispf:path` alignment with `BrickExportService.entityIri`
- `GET /api/v1/platform/brick/infer` for object path and tag-only mode

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.platform.SemanticRoundtripIntegrationTest"
```

## Smoke script

Against a running server:

```bash
bash tools/semantic-demo-check.sh
# or: ISPF_BASE_URL=${ISPF_BASE_URL:-https://ispf.example.invalid} bash tools/semantic-demo-check.sh
```

## References

- [ADR-0021 Haystack semantic overlay](decisions/0021-haystack-semantic-overlay.md)
- [ADR-0023 Haystack query runtime](decisions/0023-haystack-query-runtime.md)
