> **Язык:** русская версия (вычитка). Канонический английский: [en/tutorial-ot-credentials-vault.md](../en/tutorial-ot-credentials-vault.md).

# Туториал: credentials vault

> **Статус:** Beta — ADR-0049 Wave 4. Хаб: [OT Automation туториалы](ot-automation-excellence-tutorials.md).

## Цель

Сохранить зашифрованный API key и указать его в BPMN через `modelRef`.

Нужен secrets key: `ISPF_SECURITY_SECRETS_KEY` / `ispf.security.secrets-key`, либо разово `POST /api/v1/federation/secrets-key`. Иначе upsert → **409**.

## Шаги

### 1. Upsert

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

### 2. Describe (без секрета)

```bash
curl -s "$BASE/api/v1/credentials/by-path?path=root.platform.credentials.openai-lab" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Ожидание: `hasSecret=true`, без plaintext key.

### 3. BPMN

```text
ispf:modelRef="root.platform.credentials.openai-lab"
```

`platform-default` — по-прежнему `ISPF_AI_*` на сервере.

## Проверка

- [ ] Upsert `status=OK`
- [ ] Describe не отдаёт secret
- [ ] AI-шаг с vault `modelRef` отрабатывает (или явная ошибка auth)

## Дальше

[AI в BPMN](tutorial-ot-ai-bpmn.md)
