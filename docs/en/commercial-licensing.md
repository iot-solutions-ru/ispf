> **Language:** Canonical English. Russian edition: [ru/commercial-licensing.md](../ru/commercial-licensing.md).

# Commercial bundle licensing

RSA licensing for commercial bundles at deploy time. Architectural decision: [0003](decisions/0003-commercial-bundle-licensing.md).

## Principle

| Layer | License |
|-------|---------|
| Platform (`ispf-server`, web-console) | **GNU AGPL v3** (+ optional [LICENSE-COMMERCIAL.md](../../LICENSE-COMMERCIAL.md)) |
| Device driver pack | `licenseType` per pack — see [LICENSED_DRIVER_PACKS.md](licensed-driver-packs.md) |
| Commercial bundle | Optional `license` section in manifest; verified at deploy |

## Server configuration

```yaml
ispf:
  license:
    data-dir: ${ISPF_DATA_DIR:./data}
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

| Variable | Purpose |
|----------|---------|
| `ISPF_DATA_DIR` | Directory for `.ispf-installation-id` |
| `ISPF_LICENSE_PUBLIC_KEY_PEM` | Vendor RSA public key PEM (multiple consecutive PEM blocks allowed for rotation) |
| `ISPF_LICENSE_ENFORCE` | `true` — invalid bundle/driver/platform license blocks deploy / pack load / **server start** |

## Installation ID

File `{data-dir}/.ispf-installation-id` is created on first start.

```http
GET /api/v1/platform/installation-id
```

Admin passes `installationId` to the vendor to issue a license.

License status (admin): `GET /api/v1/platform/license` — mode, tier, valid, enforce, installationId. Card in Web Console: **System → Metrics**.

## Platform license file (`platform-license.json`)

File `{data-dir}/platform-license.json` — Enterprise exemption from AGPL (see [LICENSE-COMMERCIAL.md](../../LICENSE-COMMERCIAL.md)).

| Condition | Result |
|-----------|--------|
| File absent | Community (AGPL), start allowed |
| File + valid | Commercial tier active |
| File + invalid + `enforce=false` | WARN in log, start allowed |
| File + invalid + `enforce=true` | **Server does not start** (`IllegalStateException`) |

## `license` format in bundle

```json
"license": {
  "bundleId": "mes-reference",
  "minPlatformVersion": "0.7.0",
  "installationId": "<hex>",
  "contentSha256": "<sha256 canonical manifest without license>",
  "expiresAt": "2027-12-31T23:59:59Z",
  "signature": "<base64 RSA-SHA256 over canonical claims JSON>"
}
```

`contentSha256` — SHA-256 of manifest **without** the `license` field (canonical JSON, sorted keys).

## Vendor

CLI: [tools/license-builder/README.md](readme.md).

## Deploy behavior

| Condition | Result |
|-----------|--------|
| No `license` | Deploy as before (if `require-signed-bundles=false`) |
| No `license` + `require-signed-bundles=true` | HTTP 403 ([BL-100](roadmap.md)) |
| `license` + `enforce=false` + invalid | WARN on error, deploy continues (except `require-signed-bundles=true` → 403) |
| `license` + (`enforce=true` **or** `require-signed-bundles=true`) + invalid | HTTP 403 |

Property: `ispf.license.require-signed-bundles` / env `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES`. See [DEPLOYMENT.md § Bundle signing](deployment.md).

## IP protection after deploy (balanced policy)

RSA license protects the **delivery artifact** (manifest), not tree contents after install. The installation admin can view and modify bundle objects; they could theoretically reproduce configuration piece by piece.

**Accepted policy (ADR [0036](decisions/0036-bundle-ip-balanced-protection.md)):**

| We do | We do not |
|-------|-----------|
| Bind deploy to `installationId` | Block export / pull-from-tree |
| EULA, marketplace, activation | Encrypt JSON in the tree |
| Value in updates and support | Forbid per-object customization |
| UI: installation ID, hints on license errors | Hard DRM for operator/admin |

Copying declarative on-prem configuration **cannot be fully prevented** without harming customization; control is contract + delivery license + recurring value.

## Production key rotation (ops)

Rotate vendor RSA keys **without** changing installation ID:

| Step | Action |
|------|--------|
| 1 | Generate new key pair (`tools/license-builder/`); keep old private key until grace period ends |
| 2 | On platform: deploy **both** public keys in `ISPF_LICENSE_PUBLIC_KEY_PEM` (multiple `-----BEGIN PUBLIC KEY-----` blocks in one variable); signature is accepted if it matches any key |
| 3 | Re-issue commercial bundle / driver pack signatures for active customers |
| 4 | Grace period (recommended ≥30 days): old signatures still accepted only if public key unchanged; after key replacement old licenses are **invalid** — plan maintenance window |
| 5 | `enforce=true` on staging before prod; monitor WARN/403 in deploy logs |
| 6 | Destroy old private key after confirming all installations use new licenses |

Installation ID (`GET /api/v1/platform/installation-id`) does **not** change on rotation. Licensed driver packs use the same `ispf.license.public-key-pem` — see [LICENSED_DRIVER_PACKS.md](licensed-driver-packs.md).

## Related documents

- [PLUGINS.md](plugins.md)
- [SOLUTION_DEVELOPER_PUBLIC_API.md](solution-developer-public-api.md)
- [AIR_GAP_DEPLOYMENT.md](air-gap-deployment.md) — offline install/update (BL-128)
