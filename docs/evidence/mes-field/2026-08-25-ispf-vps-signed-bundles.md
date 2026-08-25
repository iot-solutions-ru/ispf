# VPS signed-bundles enablement (ispf-vps demostand)

| Field | Value |
| ----- | ----- |
| Date (UTC) | 2026-08-25 |
| Site | ispf.iot-solutions.ru |
| installationId | `9466af4766a35acd780a0b85e682d215fce6576cbf1c023ccd22bdb0996b5d79` |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` | **true** |
| Keys | `/opt/ispf/keys/license-{private,public}.pem` (demostand keypair; private `0600`) |
| Env backup | `/opt/ispf/ispf-server.env.bak.pre-signed.*` |

## Smoke

| Check | Result |
| ----- | ------ |
| Unsigned `mes-platform-production` deploy | **403** (require-signed) |
| Signed deploy (jar DTO hash helper) | **200** |
| MES GA smoke | **8/8** |
| AI provider | available (`Qwen/Qwen3.6-35B-A3B`) + `ISPF_LICENSE_SIGNING_PRIVATE_KEY_PEM` set for platform-generated sign |

## Notes

- Demostand rotated away from the previous vendor public key to a **local** keypair so bundles can be signed without the vendor private key.
- Until jar includes deploy `JsonNode` license verify, sign with `tools/license-builder/sign-bundle-via-jar.sh` (hashes `BundleManifest` DTO). Repo also fixes `ApplicationController` to verify raw JSON for `sign-bundle.py` parity on next jar deploy.
- **Do not** commit private PEMs or paste them in chat.
