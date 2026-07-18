> **Language:** Canonical English. Russian edition: [ru/tutorial-ot-credentials-vault.md](../ru/tutorial-ot-credentials-vault.md).

# Tutorial: credentials vault for AI / integrations

> **Status:** Beta — ADR-0049 Wave 4. Hub: [OT Automation tutorials](ot-automation-excellence-tutorials.md).

## Goal

Store an encrypted API key in the platform credentials vault and reference it from BPMN `modelRef`.

## Prerequisites

[Hub](ot-automation-excellence-tutorials.md#prerequisites). Admin rights. Do **not** commit secrets to git.

Vault encryption needs a secrets key: env `ISPF_SECURITY_SECRETS_KEY` / `ispf.security.secrets-key`, or once via `POST /api/v1/federation/secrets-key` when not already configured. Without it, credential upsert returns **409**.

## Steps

### 1. Upsert a credential

```bash
curl -s -X PUT "$BASE/api/v1/credentials/by-path?path=root.platform.credentials.openai-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "kind": "openai",
    "secret": "sk-REPLACE_ME",
    "metadata": {
      "baseUrl": "https://api.openai.com/v1",
      "model": "gpt-4o-mini"
    }
  }' | jq .
```

### 2. Describe (no secret in response)

```bash
curl -s "$BASE/api/v1/credentials/by-path?path=root.platform.credentials.openai-lab" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Expect `hasSecret=true`, metadata JSON, **no** plaintext key.

### 3. Use from BPMN

Set on an `llm_complete` task:

```text
ispf:modelRef="root.platform.credentials.openai-lab"
```

Run the workflow. The engine resolves the vault secret as API key and applies metadata `baseUrl` / `model` when present.

### 4. Fallback

`modelRef=platform-default` continues to use `ISPF_AI_*` env / `.env` on the server.

## Verify

- [ ] Upsert returns `status=OK`
- [ ] Describe never returns the raw secret
- [ ] AI BPMN step succeeds with vault `modelRef` (or clear auth error if key invalid)

## Security notes

- Paths under `root.platform.credentials.*` are the intended convention.
- Rotate keys by PUT upsert (overwrites ciphertext).
- Prefer vault paths over baking keys into BPMN XML.

## Next

[AI in BPMN](tutorial-ot-ai-bpmn.md)
