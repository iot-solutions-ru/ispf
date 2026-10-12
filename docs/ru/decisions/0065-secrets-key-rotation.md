# ADR-0065: Envelope secrets-key и ротация

## Статус

**Accepted** (2026-10-12) — Волна 1: keyed AES-GCM envelope, decrypt previous-key, re-encrypt vault/federation.

Канон: [../../en/decisions/0065-secrets-key-rotation.md](../../en/decisions/0065-secrets-key-rotation.md).

## Контекст

Волна 4 [ADR-0049](0049-ot-automation-excellence.md) добавила `IspfSecretCipher` (AES-GCM) для паролей federation, туннельных секретов и vault учётных данных. Ciphertext был Base64(`iv || ct`) без key id. Ротация `ispf.security.secrets-key` требовала удаления outbound agents; смена YAML-ключа не имела окна dual-read.

MFA/WebAuthn — [0056](0056-webauthn-idp-mfa.md), вне скоупа.

## Решение

### 1. Формат envelope

Новые записи:

```text
ispf1.<keyId>.<base64(iv || ciphertext)>
```

- Алгоритм: **AES-256-GCM** (SHA-256 от passphrase → key bytes; KDF без изменений ради совместимости).
- `keyId`: явный `ispf.security.secrets-key-id` или первые **8 hex** от `SHA-256("ispf-kid:" + passphrase)`.
- Legacy (без префикса `ispf1.`) читаются: сначала active, затем previous.

### 2. Previous key

| Свойство / env | Роль |
|----------------|------|
| `ispf.security.secrets-key` | Active encrypt + decrypt |
| `ispf.security.secrets-previous-key` | Только decrypt на время ротации |
| `*-key-id` | Опциональный явный id |

После re-encrypt — убрать previous-key и перезапустить.

### 3. API

`POST /api/v1/federation/secrets-key/reencrypt` (admin) перешифровывает credentials + outbound agents + peer auth secrets под active ключ. Status отдаёт `activeKeyId` и `previousKeyConfigured`.

### 4. UI (DATABASE)

Если ключ не задан в YAML/env, `POST …/secrets-key` может сменить DATABASE-ключ с in-process dual-read и re-encrypt. YAML-ключ по-прежнему нельзя перезаписать из UI (`409`).

## Последствия

- Тесты: `IspfSecretCipherTest`.
- Follow-up ротации ключей в ADR-0049 Wave 4 закрыт этим ADR; marketplace `workflow-template` installer остаётся открытым.
- HKDF / внешний KMS — follow-up.

## Связанное

- [0049](0049-ot-automation-excellence.md), [security](../security.md), [federation](../federation.md)
