# ADR-0056: WebAuthn / IdP OTP as MFA follow-up (BL-194)

## Status

**Proposed** (2026-09-05) — product intent locked; implementation **not** started.  
Parent: BL-153 TOTP GA **Done**; backlog row **BL-194 Planned**.

## Context

Enterprise tenders increasingly ask for phishing-resistant MFA (passkeys / WebAuthn) or IdP-enforced OTP (Keycloak / Entra). ISPF already ships **persisted TOTP** with optional `required-for-admin` (BL-153). Extending TOTP further would duplicate IdP capabilities and delay passkeys.

## Decision

1. **Keep TOTP** as the always-available local MFA path (air-gap / no IdP).
2. **BL-194 delivers two complementary paths** (either may ship first if a named tender blocks):
   - **WebAuthn / passkeys** for local (and optionally OIDC-linked) users: registration ceremony, login `publicKey` assertion, recoverable backup policy documented.
   - **IdP OTP / WebAuthn via Keycloak (or compatible OIDC IdP)** as the **preferred** MFA when `ISPF` is configured for OIDC — platform does not re-implement OTP UX; it requires `acr`/`amr` claims agreed in realm settings.
3. **Default MFA remains off** until admin enables TOTP and/or IdP policies — same honesty as today.
4. **No claim** of “passwordless-only” org until WebAuthn recovery and IdP fallback are documented in [security.md](../security.md).

### Non-goals (this ADR)

- Replacing RBAC / per-variable ACL (BL-154 Done).
- Hardware security module mandate.
- Closing G-01 pen-test by itself.

## Consequences

### Positive

- Clear split: air-gap TOTP vs enterprise IdP / passkeys.
- Tender language can point at BL-194 without overselling TOTP as WebAuthn.

### Negative / work

- New browser ceremonies + attestation policy choices.
- Test matrix: Chrome/Safari/Android; recover-when-device-lost.
- Console + API + docs EN/RU; scorecard Security rescore only after evidence.

## Implementation sketch (when unparked)

| Slice | Deliverable |
|-------|-------------|
| A | `ispf.security.webauthn.*` flags; Relying Party id/origins config |
| B | Enrollment + login APIs; store credential ids hashed; audit events |
| C | Web Console: register / assert passkey; keep TOTP parallel |
| D | OIDC docs: Keycloak OTP/WebAuthn as primary MFA; ISPF claim checks |
| E | Evidence under `docs/evidence/security-webauthn/` + update G-04 |

**Unpark trigger:** named tender or customer MFA requirement (see [parked-backlog](../parked-backlog.md) **P-WEBAUTHN** when present).

## References

- [security.md](../security.md) — MFA section  
- [compliance-tender-pack.md](../compliance-tender-pack.md) — G-04  
- [pen-test-scope.md](../pen-test-scope.md) — G-01 prep  
- Roadmap BL-194
