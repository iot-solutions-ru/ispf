> **Language:** Canonical English. Russian edition: [ru/license.md](../ru/license.md).

# License and delivery boundaries

## Platform (ISPF core)

**IoT Solutions Platform Framework (ISPF)** — platform shell and monorepo core:

| Path | License |
|------|---------|
| `packages/ispf-core`, `packages/ispf-server`, `packages/ispf-expression`, … | [GNU AGPL v3](../LICENSE) |
| `apps/web-console/` | [GNU AGPL v3](../LICENSE) |
| `docs/` | [GNU AGPL v3](../LICENSE) |

**Dual licensing:** community uses AGPL; enterprise — [LICENSE-COMMERCIAL.md](../../LICENSE-COMMERCIAL.md) + `platform-license.json` (AGPL obligation exemption under contract).

Copyright: **© 2026 ISPF Core Contributors** — see [LICENSE](../LICENSE) and [NOTICE](../NOTICE).

## Device drivers — driver packs only

Drivers are **not** included in `ispf-server.jar`. Each protocol is a separate pack:

```text
${ISPF_DRIVER_PACKS_DIR}/ispf-driver-modbus/
  driver-pack.json    ← licenseType, driverId
  LICENSE
  ispf-driver-modbus.jar
```

Build: `.\gradlew syncAllDriverPacks` → `build/driver-packs/`.

Details: [LICENSED_DRIVER_PACKS.md](licensed-driver-packs.md), ADR [0016](decisions/0016-agpl-dual-licensing.md).

## Application bundles — separate product

Bundle (objects, dashboards, widgets, functions) is a **separate artifact** with **its own EULA**:

| Type | License |
|------|---------|
| Open reference (`lab-training`, …) | Apache / AGPL manifest, no RSA |
| Commercial SKU | Proprietary EULA + optional RSA in manifest ([COMMERCIAL_LICENSING.md](commercial-licensing.md)) |

Declarative bundle JSON is **not** platform source code; AGPL platform **does not require** its disclosure.

## License boundaries (summary)

| Artifact | AGPL disclosure | Commercial license |
|----------|-----------------|-------------------|
| Fork / patches to `ispf-server` or web-console | Yes (network use) | Enterprise EULA |
| Declarative application bundle | Usually no | Bundle EULA (+ RSA) |
| Driver pack | Per pack `licenseType` + deps | Enterprise+ for signed packs |

## What is not in the AGPL core repo

| Type | Where | License |
|------|-------|---------|
| Industry reference stands | Separate branch / repo | Per delivery |
| Commercial plugins | Separate repo | EULA in package |
| Customer app bundle | Project repo | Per contract |

## Distribution obligations

1. Retain [LICENSE](../LICENSE) and [NOTICE](../NOTICE).
2. Include [THIRD_PARTY_NOTICES.md](third-party-notices.md) and follow [LICENSE_COMPLIANCE.md](license-compliance.md).
3. For driver packs — LICENSE and notices of each pack.
4. Comply with AGPL / GPL / LGPL / MPL dependencies in packs.

## Related documents

- [LICENSE-COMMERCIAL.md](../../LICENSE-COMMERCIAL.md)
- [LICENSE_COMPLIANCE.md](license-compliance.md)
- [COMMERCIAL_LICENSING.md](commercial-licensing.md)
- [CLA.md](../../CLA.md)
- [PLUGINS.md](plugins.md)
