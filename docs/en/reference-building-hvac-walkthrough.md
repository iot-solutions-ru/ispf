# Building HVAC reference walkthrough

Third reference application for solution developers: **office floor zones**, comfort setpoints, and Haystack-oriented metadata — without custom Java in `ispf-server`.

Artifacts: [examples/building-hvac-app/](../../examples/building-hvac-app/), bundle `appId` = `building-hvac`.

## Domain

![Building HVAC operator HMI](../assets/ispf-operator-building-hvac.png)

| Entity | Description |
|--------|-------------|
| **Zone** (`hvac_zone`) | Floor zone code, space temperature, setpoint, HVAC mode |
| **BFF host** | `root.platform.devices.demo-sensor-01` — `hvac_listZones` |
| **Operator shell** | `?mode=operator&app=building-hvac` |

Seed zones: `L1-OPEN-01` (cool), `L2-MEET-02` (heat).

## Steps

| # | Action | API / path |
|---|--------|------------|
| 1 | Deploy bundle | `POST /api/v1/applications/building-hvac/deploy` |
| 2 | List zones | BFF `hvac_listZones` @ `demo-sensor-01` |
| 3 | Solution catalog | System → Solutions → **Install** reference `building-hvac-app` |
| 4 | Operator demo | `/?mode=operator&app=building-hvac` |

## Haystack

Bundle `metadata.haystackTags` documents expected tag vocabulary (`site`, `equip`, `ahu`, `zone`, `temp`). Bind real devices via `haystack-metadata-v1` relative model when extending the walkthrough.

## CI

- `BuildingHvacBundleSmokeTest` — deploy + `hvac_listZones` returns 2 rows
- Optional: `examples/warehouse-app/.github/workflows/bundle-ci.yml` pattern for integrator CI

## Commands

```bash
./gradlew :packages:ispf-server:test --tests com.ispf.server.application.BuildingHvacBundleSmokeTest
```
