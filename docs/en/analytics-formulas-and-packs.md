> **Language:** Canonical English. Russian edition: [ru/analytics-formulas-and-packs.md](../ru/analytics-formulas-and-packs.md).

# Analytics formulas and extension packs

How to create, reuse, and distribute historian computations beyond a single binding rule. Covers **Tier A** (built-ins), **Tier B** (user formulas), and **Tier C** (Java extension packs + marketplace).

**See also:** [analytics-historian-cookbook.md](analytics-historian-cookbook.md) (recipes), [ADR-0042](decisions/0042-analytics-function-catalog.md) (architecture), [analytics-tag-catalog.md](analytics-tag-catalog.md) (deployed tags API).

> **Note:** [historian-tiers.md](historian-tiers.md) describes **storage tiers** (hot/warm/cold). This document uses **function tiers** A/B/C from ADR-0042 — different topic.

---

## Three function tiers

| Tier | What it is | Who creates | Example |
|------|------------|-------------|---------|
| **A — Built-in** | Core helpers + CEL `hist.*` + open packs on classpath | Platform team | `rollingAvg`, `hist.avg`, `energyDelta` (core-ext) |
| **B — User formula** | Parameterized expression template | Operator / solution dev | `rateOfChange({{levelPath}}, 1h) * {{tankArea}}` |
| **C — Extension pack** | JAR with `AnalyticsFunctionProvider` SPI | Partner / ISV | `mes-oee-pack`, `water-quality-pack` |

**Principle:** the catalog is for **discovery and insertion**; the **binding rule** on a device is the deployed instance.

```
Catalog (browse)  →  parameterize  →  binding rule on device  →  live variable + historian tag
```

---

## Tier A — Built-in functions

### Helper syntax (historian)

| Function | Example | Notes |
|----------|---------|-------|
| `rollingAvg` | `rollingAvg(root.devices.pump01.temperature, 5m)` | Windowed average |
| `rateOfChange` | `rateOfChange(path.level, 1h)` | Delta first→last bucket avg |
| `oee` | `oee('path', 'avail', 'perf', 'qual', 8h)` | OEE % from four inputs |
| `totalizer` | `totalizer(path.energy, 1h)` | Sum over window |
| `min` / `max` / `last` | `min(path.var, 5m)` | Bucket aggregates |

### CEL + historian (`helper: cel`)

```text
hist.avg('root.devices.pump01', 'temperature', '5m')
hist.min('root.devices.pump01', 'temperature', 'value', '1h')
(hist.avg('…sensor-a', 'temperature', '5m') + hist.avg('…sensor-b', 'temperature', '5m')) / 2.0
```

Available: `hist.avg`, `hist.min`, `hist.max`, `hist.sum`, `hist.last`, `hist.live`.

Use **double literals** in CEL (`2.0`, not `2`) when mixing with `hist.*`.

### Discovery

- **Web console:** Object inspector → **Computations** → **+ Rule** → **Historian** → expression editor → **function catalog** (search, filter by kind/tag/pack).
- **API:** `GET /api/v1/platform/analytics/catalog` and `GET .../catalog/{functionId}`.

You **cannot** add a new `hist.xyz` from the UI — `hist.*` macros are registered server-side.

---

## Tier B — User-defined formulas

Tier B is a **reusable template**, not a new runtime primitive. It expands to Tier A syntax at compile time.

### Placeholder syntax

Parameters use double braces:

```text
rollingAvg({{sourcePath}}, {{window}})
rateOfChange({{levelPath}}, 1h) * {{tankArea}}
hist.avg('{{devicePath}}', '{{variable}}', '{{window}}')
```

Names must match `[a-zA-Z][a-zA-Z0-9_]*`. Server and UI auto-detect parameters from the expression.

### Create via web console

**From an expression:**

1. Open the historian expression editor (Computations → **+ Rule** → Historian).
2. Write the expression with `{{param}}` placeholders.
3. Click **Save as formula**.
4. Set **id**, **display name**, **kind** (`historian` or `reactive`).
5. Save — formula is stored in `@analyticsFormulas` on `root.platform`.

**From the formula manager:**

- **System → Analytics formulas** — list, create, edit, delete site-wide formulas.

### Apply a saved formula

1. In the expression editor catalog, find your formula (pack `site` or `app:<appId>`).
2. Click **Apply** → fill parameters (tag paths, windows, numbers).
3. **Validate** → **Apply** to the rule.

Rules created from a formula store `formulaRef` + `formulaParams` in rule metadata. When the formula is updated, linked rules are **re-expanded** automatically.

### Storage scopes

| Scope | Location | Who |
|-------|----------|-----|
| Site-wide | `root.platform` variable `@analyticsFormulas` | Platform admin |
| Application | `analytics-formulas.json` in app bundle | App developer |
| Blueprint | `analyticsFormulasJson` in RELATIVE blueprint | Blueprint author |

### REST API

Base path: `/api/v1/platform/analytics/formulas`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/formulas?scope=site` | List site formulas |
| GET | `/formulas?scope=app&appId=` | List app formulas |
| GET | `/formulas/{id}?scope=&appId=` | Get one formula |
| POST | `/formulas` | Create (body: `AnalyticsFormula`) |
| PUT | `/formulas/{id}?scope=&appId=` | Update; returns `reboundRules` count |
| DELETE | `/formulas/{id}?scope=&appId=` | Delete |
| POST | `/formulas/{id}/expand` | Expand template with parameters |

**Expand request body:**

```json
{
  "scope": "site",
  "appId": null,
  "parameters": {
    "levelPath": "root.platform.devices.tank01.level",
    "tankArea": "12.5"
  }
}
```

**Formula record example:**

```json
{
  "id": "tank-fill-rate",
  "displayName": "Tank fill rate (m³/h)",
  "kind": "historian",
  "expression": "rateOfChange({{levelPath}}, 1h) * {{tankArea}}",
  "parameters": [
    { "name": "levelPath", "type": "tagPath", "required": true },
    { "name": "tankArea", "type": "number", "required": false, "defaultValue": "1" }
  ],
  "scope": "site",
  "version": 1
}
```

### What Tier B cannot do

| Need | Use instead |
|------|-------------|
| New bucket aggregation semantics | Tier C Java pack |
| Procedural logic / external IO per tick | Reactive rule + `callFunction` (live only) |
| Arbitrary JavaScript in historian | Not supported (breaks backfill determinism) |

---

## Tier C — Extension packs (Java SPI)

For **new historian-safe functions** with custom bucket logic (e.g. certified industry KPIs).

### Pack layout (on-disk, planned GA)

```text
${ISPF_ANALYTICS_PACKS_DIR}/
  acme-mes-kpi/
    analytics-pack.json
    acme-mes-kpi.jar
```

### `analytics-pack.json`

| Field | Required | Description |
|-------|:--------:|-------------|
| `packId` | ✓ | Stable pack id (catalog `pack` field) |
| `version` | ✓ | Semver |
| `licenseType` | ✓ | SPDX or commercial ref |
| `minPlatformVersion` | ✓ | ISPF semver gate |
| `jarFile` | ✓ | JAR filename in pack directory |
| `functions[]` | ✓ | Helper ids exposed by this pack |
| `license` | commercial | RSA-signed claims (same model as [licensed-driver-packs.md](licensed-driver-packs.md)) |

Example:

```json
{
  "packId": "acme-mes-kpi",
  "version": "1.0.0",
  "licenseType": "LicenseRef-Acme-Commercial",
  "minPlatformVersion": "0.9.127",
  "jarFile": "acme-mes-kpi.jar",
  "functions": ["batchYield", "downtimePct"],
  "license": {
    "packId": "acme-mes-kpi",
    "minPlatformVersion": "0.9.127",
    "installationId": "<from GET /api/v1/platform/installation-id>",
    "jarSha256": "<sha256 of JAR>",
    "expiresAt": "2027-12-31T23:59:59Z",
    "signature": "<base64 RSA-SHA256>"
  }
}
```

### SPI contract

1. Implement `com.ispf.analytics.spi.AnalyticsFunctionProvider`.
2. Register via `META-INF/services/com.ispf.analytics.spi.AnalyticsFunctionProvider`.
3. Provide `AnalyticsFunctionDescriptor` + `AnalyticsEvaluator` (must use `HistorianPort` for historian-safe reads).

**Reference implementation:** `packages/ispf-analytics-core-ext` — open pack `core-ext` with `energyDelta`.

```java
// EnergyDeltaFunctionProvider.java — minimal pattern
public AnalyticsFunctionDescriptor getDescriptor() {
    return new AnalyticsFunctionDescriptor(
        "energyDelta", "Energy delta", "energyDelta",
        "energyDelta(sourcePath, window)",
        List.of(/* parameters */),
        Set.of("energy", "historian"),
        "core-ext"
    );
}
```

### Runtime (today)

- Open packs on the server classpath are loaded at startup by `AnalyticsPackLoader` (`ServiceLoader`).
- Drop-in directory loader + license enforcement mirrors driver packs — **BL-216** (see roadmap).

### Configuration (planned GA)

```yaml
ispf:
  analytics:
    packs-dir: ${ISPF_ANALYTICS_PACKS_DIR:/opt/ispf/analytics-packs}
  license:
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

---

## Buying Tier C packs on the marketplace

Commercial and community **analytics extension packs** distribute through the same marketplace contract as application bundles and symbol packs ([MARKETPLACE.md](marketplace.md)).

**Status:** listing contract and operator flow documented here; platform install into `ISPF_ANALYTICS_PACKS_DIR` — **BL-216** (in progress). Open pack `energyDelta` ships in core today without marketplace.

### Operator flow

| Step | Action |
|------|--------|
| 1 | **System → Solutions → Marketplace** — browse or search `tags: analytics`, `artifactKind: analytics-pack` |
| 2 | **Free pack** — **Install** → platform downloads archive → unpacks to `ISPF_ANALYTICS_PACKS_DIR` → functions appear in analytics catalog |
| 3 | **Paid pack** — purchase from vendor → receive activation code → **Activate** with entitlement key (same as paid app bundles) |
| 4 | **Verify** — `GET /api/v1/platform/analytics/catalog` lists new helpers with `pack: <packId>` |
| 5 | **Deploy** — create historian rules using new helpers (e.g. `batchYield(path.output, 8h)`) |

Paid listings without a valid key show vendor contact link; functions are not registered until activation succeeds.

### Listing manifest (vendor)

Extend the standard marketplace listing with analytics-specific fields:

```json
{
  "slug": "acme-mes-kpi",
  "title": "Acme MES KPI Pack",
  "description": "Historian-safe batch yield, downtime %, and scrap rate helpers.",
  "pricing": "paid",
  "artifactKind": "analytics-pack",
  "appId": null,
  "packId": "acme-mes-kpi",
  "vendorName": "Acme Analytics",
  "priceCents": 49900,
  "latestVersion": "1.0.0",
  "minIspfVersion": "0.9.127",
  "tags": ["analytics", "mes", "kpi", "historian"],
  "bundleArtifact": "acme-mes-kpi-1.0.0.zip"
}
```

Archive contents:

```text
acme-mes-kpi-1.0.0.zip
  analytics-pack.json
  acme-mes-kpi.jar
  LICENSE
  NOTICE
```

### Platform API (proxied via marketplace)

| Method | Path |
|--------|------|
| GET | `/api/v1/solutions/marketplaces/{id}/catalog?q=analytics&pricing=` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/install` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/activate` |

Activate body: `{ "activationCode": "..." }` — `installationId` added server-side ([ADR-0003](decisions/0003-commercial-bundle-licensing.md)).

### Vendor checklist

1. Implement SPI pack; test against `ispf-analytics-api`.
2. Build signed `analytics-pack.json` + JAR; run license builder ([commercial-licensing.md](commercial-licensing.md)).
3. Publish listing on [ispf-marketplace](https://github.com/Michaael/ispf-marketplace) with `artifactKind: analytics-pack`.
4. Interop CI: functions appear in catalog after install on lab ISPF.
5. Partner program: OEM tier for marketplace revenue share ([partner-program.md](partner-program.md)).

### Offline / air-gapped

Copy the pack zip to the server and unpack under `ISPF_ANALYTICS_PACKS_DIR` manually; place RSA license file per `analytics-pack.json`. Restart ISPF or call planned `POST /api/v1/platform/analytics/packs/reload`.

---

## Choosing the right tier

| Goal | Tier | Effort |
|------|------|--------|
| Average / min / max over window | A (`hist.*` or helper) | Minutes |
| Same formula on many assets | B (user formula) | Minutes |
| Composite of existing functions | A (CEL) or B (template) | Minutes–hours |
| Certified industry KPI / custom bucket math | C (Java pack) | Days–weeks |
| Buy certified KPI from vendor | C + marketplace | Purchase + install |

---

## Related

- [ADR-0042](decisions/0042-analytics-function-catalog.md)
- [analytics-historian-cookbook.md](analytics-historian-cookbook.md)
- [analytics-tag-catalog.md](analytics-tag-catalog.md)
- [MARKETPLACE.md](marketplace.md)
- [licensed-driver-packs.md](licensed-driver-packs.md) — parallel licensing model
- [analytics-platform-roadmap.md](analytics-platform-roadmap.md) — BL-212…216
