# License compliance (engineering checklist)

> **Status:** Stable — Obligations checklist. Hub: [doc-status.md](doc-status.md).

Engineering procedures for ISPF releases. **Not legal advice** — counsel review required
for commercial contracts and third-party asset redistribution.

## Platform license modes

| Mode | When | Obligations |
|------|------|-------------|
| **Community (AGPL)** | Default; no `platform-license.json` | Network use → AGPL source-offer for **platform** modifications |
| **Enterprise** | Valid `platform-license.json` | Per [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md) |

Check runtime: `GET /api/v1/platform/license`

## Binary distribution bundle

Ship with every release:

1. [LICENSE](../../LICENSE) (AGPL)
2. [NOTICE](../../NOTICE)
3. [third-party-notices](third-party-notices.md)
4. Java + npm + driver-pack SBOM (CycloneDX; `node tools/license-audit/generate-sbom.mjs`)
5. Engineering legal review: [sbom-legal-review](sbom-legal-review.md) / `build/sbom/LEGAL-REVIEW.md`
6. Per driver pack: `LICENSE`, `THIRD_PARTY-NOTICE.txt` (if any)

Web console static files include `legal/*` (copied at build via `scripts/copy-legal-assets.mjs`).

## Driver pack deploy profiles

| Profile | Use case |
|---------|----------|
| `permissive` (**default**) | All Apache-2.0 packs + NIST public-domain SIP |
| `all` | Same set today (no GPL/LGPL/MPL/StepFunc packs remain in catalog) |

Former copyleft packs (BACnet, DLMS, IEC-104, DNP3, IPMI, RADIUS, M-Bus) now ship **ISPF-owned codecs** with `licenseType: Apache-2.0`.

VPS deploy:

```powershell
.\deploy\vps-deploy-direct.ps1 -Version <version> -SkipTests -DriverPackProfile permissive
```

## P&ID symbol pack

Original ISA/ISO functional symbols — **Apache-2.0**, built by [`tools/symbol-pack-isa`](../../tools/symbol-pack-isa).

## Pre-release audit (automated)

```bash
node tools/license-audit/check-all.mjs
node tools/license-audit/generate-sbom.mjs
cd apps/web-console && npm ci && npm run build
./gradlew syncAllDriverPacks
```

CI runs `check-all.mjs` on every push/PR.

## Reports

Spreadsheet Band1 templates use **Apache POI only**. YARG / JasperReports / docx4j report path removed from `ispf-server`.

## Related

- [license](license.md)
- [third-party-notices](third-party-notices.md)
- [sbom-legal-review](sbom-legal-review.md)
- [licensed-driver-packs](licensed-driver-packs.md)
- [commercial-licensing](commercial-licensing.md)
- [fto-aggregate-context-model](fto-aggregate-context-model.md) — FTO memo vs AggreGate object model
