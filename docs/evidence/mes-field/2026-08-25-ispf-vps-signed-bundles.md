# VPS signed-bundles enablement (ispf-vps demostand)

| Field | Value |
| ----- | ----- |
| Date (UTC) | 2026-08-25 |
| Site | ispf.iot-solutions.ru |
| ISPF version | **0.9.188** (jar deploy 2026-08-25) |
| installationId | `9466af4766a35acd780a0b85e682d215fce6576cbf1c023ccd22bdb0996b5d79` |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` | **true** |
| Keys | `/opt/ispf/keys/license-{private,public}.pem` (demostand keypair; private `0600`) |
| Env backup | `/opt/ispf/ispf-server.env.bak.pre-signed.*` |
| Jar backup | `/opt/ispf/ispf-server.jar.bak.0.9.186.*` |

## Smoke

| Check | Result |
| ----- | ------ |
| Unsigned `mes-platform-production` deploy | **403** (require-signed) |
| Signed deploy (`sign-bundle.py` raw JSON) | **200** on **0.9.188** |
| Signed deploy (jar DTO helper) | **200** on 0.9.186 |
| MES GA smoke | **8/8** |
| AI provider | available (`Qwen/Qwen3.6-35B-A3B`) + signing private key in env |
| Flyway | V89 `random_uuid()` applied |

## Notes

- Demostand uses a **local** license keypair (not the previous vendor public-only key).
- **0.9.188** verifies license on the raw JSON request map — `tools/license-builder/sign-bundle.py` works without the jar helper.
- **Do not** commit private PEMs or paste them in chat.
