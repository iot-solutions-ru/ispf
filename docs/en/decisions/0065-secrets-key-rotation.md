# ADR-0065: Secrets-key envelope and rotation

## Status

**Accepted** (2026-10-12) — Wave 1 lands keyed AES-GCM envelopes, previous-key decrypt, and vault/federation re-encrypt.

## Context

[ADR-0049](0049-ot-automation-excellence.md) Wave 4 introduced `IspfSecretCipher` (AES-GCM) for federation service-account passwords, outbound tunnel secrets, and the platform credentials vault. Ciphertext was raw Base64(`iv || ct`) with no key identifier. Operators could not rotate `ispf.security.secrets-key` without deleting outbound agents, and YAML key changes required downtime with no dual-read window.

Related: [0056](0056-webauthn-idp-mfa.md) covers MFA/WebAuthn — out of scope here.

## Decision

### 1. Envelope format

New writes use:

```text
ispf1.<keyId>.<base64(iv || ciphertext)>
```

- Algorithm remains **AES-256-GCM** (SHA-256 of the passphrase as key bytes — unchanged KDF for compatibility with existing ciphertext).
- `keyId` is either an explicit `ispf.security.secrets-key-id` (1–32 of `[A-Za-z0-9._-]`) or the first **8 hex** chars of `SHA-256("ispf-kid:" + passphrase)`.
- Legacy values (no `ispf1.` prefix) stay readable: try active key, then previous key.

### 2. Previous key (dual-read)

| Property / env | Role |
|----------------|------|
| `ispf.security.secrets-key` / `ISPF_SECURITY_SECRETS_KEY` | Active encrypt + decrypt |
| `ispf.security.secrets-key-id` | Optional explicit active key id |
| `ispf.security.secrets-previous-key` / `ISPF_SECURITY_SECRETS_PREVIOUS_KEY` | Decrypt-only during rotation |
| `ispf.security.secrets-previous-key-id` | Optional explicit previous key id |

After re-encrypt, clear the previous-key settings and restart.

### 3. Re-encrypt API

`POST /api/v1/federation/secrets-key/reencrypt` (admin) decrypts and re-encrypts under the **active** key:

- `platform_credentials.cipher_text`
- `federation_outbound_agents.registration_code_enc` / `session_token_enc`
- `federation_peers.auth_secret_enc`

Rows already under the active key id are skipped. Status: `GET /api/v1/federation/secrets-key/status` exposes `activeKeyId` and `previousKeyConfigured`.

### 4. UI (DATABASE) key rotation

When secrets-key is **not** set in YAML/env, `POST /api/v1/federation/secrets-key` may **change** an existing DATABASE key: in-process dual-read overlay → re-encrypt all → persist the new key. YAML-configured keys still reject UI overwrite (`409`).

### 5. Operator runbook (YAML)

1. Set `ISPF_SECURITY_SECRETS_PREVIOUS_KEY` to the old value; set `ISPF_SECURITY_SECRETS_KEY` to the new value; restart.
2. `POST /api/v1/federation/secrets-key/reencrypt`.
3. Remove previous-key env/config; restart.
4. Confirm `previousKeyConfigured=false` and new ciphertext uses `ispf1.<newKid>.…`.

## Consequences

- Fitness: `IspfSecretCipherTest` (envelope, legacy, previous-key, unknown kid).
- ADR-0049 Wave 4 key-rotation follow-up is closed by this ADR; marketplace `workflow-template` installer remains open.
- HKDF / external KMS remain follow-ups — not required to close the field rotation gap.

### Risks

- Operators who rotate YAML without setting previous-key lose decrypt for legacy rows until the old key is restored.
- Envelope length grows slightly (`ispf1.` + kid + `.`); existing `VARCHAR(1024)` federation columns remain sufficient for typical secrets.
- Explicit key ids that collide between active and previous must be avoided (UI rotation derives ids and rejects collisions).

## Related

- [0066-security-model](0066-security-model.md) — platform security claim boundary
- [0049-ot-automation-excellence](0049-ot-automation-excellence.md)
- [tutorial-ot-credentials-vault](../tutorial-ot-credentials-vault.md)
- [security](../security.md), [federation](../federation.md)
