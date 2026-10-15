# Security Policy

## Supported Versions

ISPF publishes pre-1.0 releases on the `0.9.x` line. Security fixes land on **`main`** and ship in the next GitHub release.

| Version | Supported |
| ------- | --------- |
| Latest `0.9.x` release ([Releases](https://github.com/iot-solutions-ru/ispf/releases)) | Yes |
| Older `0.9.x` tags | Best effort — upgrade to the latest release |
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
