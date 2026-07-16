# License compliance (engineering checklist)

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
4. Java + npm SBOM (CycloneDX recommended)
5. Per driver pack: `LICENSE`, `THIRD_PARTY-NOTICE.txt`, `NOTICE-EXTERNAL-DEPS.txt` (if any)

Web console static files include `legal/*` (copied at build via `scripts/copy-legal-assets.mjs`).

## Driver pack deploy profiles

| Profile | Use case |
|---------|----------|
| `permissive` (**default** for VPS deploy) | Apache-2.0 + public-domain packs only |
| `all` | Full monorepo build including GPL/LGPL/MPL/StepFunc-restricted |

VPS deploy:

```powershell
.\deploy\vps-deploy-direct.ps1 -Version 0.9.32 -SkipTests -DriverPackProfile permissive
```

Copyleft or StepFunc-restricted packs require separate legal review before `-DriverPackProfile all`.

## Restricted packs (not in permissive profile)

| Pack | licenseType | Notes |
|------|-------------|-------|
| `ispf-driver-bacnet` | GPL-3.0-only | bacnet4j |
| `ispf-driver-dlms` | GPL-2.0-only | Gurux |
| `ispf-driver-iec104*` | GPL-3.0-or-later | j60870 |
| `ispf-driver-mbus` | MPL-2.0 | jMBus |
| `ispf-driver-radius` | LGPL-3.0-or-later | TinyRadius |
| `ispf-driver-ipmi` | GPL-3.0-or-later | vxIPMI (Verax) |
| `ispf-driver-dnp3` | LicenseRef-StepFunc-NonCommercial | **io.stepfunc:dnp3 not bundled** — see pack `NOTICE-EXTERNAL-DEPS.txt` |

## P&ID symbol pack

Original ISA/ISO functional symbols — **Apache-2.0**, built by [`tools/symbol-pack-isa`](../../tools/symbol-pack-isa).

- [license](license.md)
- [pid-symbols-legal](pid-symbols-legal.md)

## Pre-release audit (automated)

```bash
node tools/license-audit/check-all.mjs
cd apps/web-console && npm ci && npm run build
./gradlew syncAllDriverPacks
```

CI runs `check-all.mjs` on every push/PR.

## bpmn-js watermark

Workflow BPMN editor/viewer must keep the bpmn.io watermark visible. CSS in
`apps/web-console/src/styles.css` enforces visibility; verify manually in UI before release.

## Related

- [license](license.md)
- [third-party-notices](third-party-notices.md)
- [licensed-driver-packs](licensed-driver-packs.md)
- [commercial-licensing](commercial-licensing.md)
