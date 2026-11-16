# Security Policy

## Supported Versions

ISPF **1.0.0** (2026-11-16) is the first stable release. Security fixes land on **`main`** and ship in the next release. Supported line: latest `1.0.x` ([ADR-0064](docs/en/decisions/0064-release-compatibility-lts.md)).

| Version | Supported |
| ------- | --------- |
| Latest `1.0.x` release ([Releases](https://github.com/iot-solutions-ru/ispf/releases)) | Yes |
| Older `1.0.x` tags | Upgrade to the latest `1.0.x` release |
| `0.9.x` tags | Upgrade to 1.0.0 |
| Unreleased `main` | Development only |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Prefer one of:

1. **[GitHub Private Vulnerability Reporting](https://github.com/iot-solutions-ru/ispf/security/advisories/new)** for this repository (Security → Advisories → Report a vulnerability), or
2. A **private** channel to the maintainers (organization [iot-solutions-ru](https://github.com/iot-solutions-ru)); include a contact email so we can reply.

Include:

- Affected version / commit / deploy shape (portable JAR, container, from source)
- Steps to reproduce and impact (auth bypass, ACL, injection, DoS, secret exposure, etc.)
- Whether a fix or workaround is already known

We aim to acknowledge within **7 days** and to keep you updated while the issue is triaged. If the report is accepted, we coordinate disclosure (usually after a fix is released or a mitigation is documented). Declined reports get a short explanation.

## Out of scope (usual lab / local defaults)

Default lab credentials (`admin` / `admin` and similar), the `local` / `test` Spring profiles, and intentionally open lab demostands are **not** production security defects. See [docs/en/security.md](docs/en/security.md) for RBAC, production hardening, and related guidance.

Product / architecture security notes: [docs/en/security.md](docs/en/security.md).
