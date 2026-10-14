# ADR-0076: External secrets providers (beyond process envelope)

## Status

**Accepted** (2026-10-13) — amends [0065](0065-secrets-key-rotation.md): envelope+rotation in-process stays the default; external KMS/HSM/Vault and GOST crypto are **explicit non-claims** until a named tender.

**Tension program (Wave 3B):** [adr-tension-resolution](../adr-tension-resolution.md) keeps **Secrets key / envelope** **Closed** on the 0065 envelope (shipped). **External KMS / GOST** is **Closed** here as **policy non-claim** — no provider SPI in the platform until a **named customer tender** authorizes design + implementation; until then, sales and scorecards must not imply Vault/KMS/HSM/GOST in product.

## Context

0065 closed the “no key id / no rotation” gap with `ispf1.<keyId>.…`. Enterprise tenders still ask for Vault/KMS/HSM or GOST TLS/crypto. Those must not be silently implied by 0065.

## Decision

### 1. Provider SPI (direction)

| Provider | Role | Status |
|----------|------|--------|
| **env / DATABASE UI key** | Default master material for envelope | **Done** (0065) |
| **External KMS** (cloud or on-prem) | Wraps/unwraps data keys | **Not started** — SPI **only** under named tender |
| **HSM** | Same, hardware-backed | **Not started** — SPI **only** under named tender |
| **HashiCorp Vault** (or equivalent) | Transit / KV for master or data keys | **Not started** — SPI **only** under named tender |
| **GOST crypto** | TLS and/or envelope algorithms for КИИ РФ | **Not started** — separate compliance track; **only** under named tender / КИИ ADR amendment |

**Wave 3B (docs):** This ADR closes the external-provider tension as an honest **non-claim**. Implementing `SecretsProvider` (or equivalent) remains **out of scope** until tender + ADR amendment name the provider, migration, and reencrypt contract — same spirit as 0065, no plaintext logging.

### 2. Hard rules

1. Platform credentials and federation secrets remain **envelope ciphertext in PG** unless a future provider stores references only.
2. Switching provider requires a reencrypt job (same spirit as 0065), never plaintext logging.
3. GOST appears only under a named customer/compliance ADR amendment — ISA-95 “GOST” docs are unrelated.

## Consequences

- Security 9.5 still needs pen-test (G-01) + optional provider work.
- No code in this ADR.

## Related

- [0065-secrets-key-rotation](0065-secrets-key-rotation.md), [security](../security.md), [compliance-tender-pack](../compliance-tender-pack.md)
