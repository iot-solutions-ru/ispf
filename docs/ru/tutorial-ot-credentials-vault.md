> **Язык:** русская версия. Английский оригинал: [en/tutorial-ot-credentials-vault.md](../en/tutorial-ot-credentials-vault.md).

# Урок 5: Зашифрованное хранилище учётных данных и ключей (`Credentials Vault`)

> **Статус:** Beta — безопасное хранение паролей и API-ключей внешних систем в ветке `root.platform.credentials.*` с шифрованием в БД и ротацией ключей ([ADR-0065](decisions/0065-secrets-key-rotation.md)). См. также: [Все уроки серии](ot-automation-excellence-tutorials.md).

## Цель урока
Сохранить секретный ключ внешнего сервиса в зашифрованном хранилище ISPF и подключить его к шагу процесса BPMN через ссылку `modelRef`, не раскрывая секрет в открытом виде в манифестах и журналах.

---

## Предварительное условие: мастер-ключ шифрования

На сервере должна быть задана переменная окружения `ISPF_SECURITY_SECRETS_KEY` (свойство `ispf.security.secrets-key`). Все секреты шифруются алгоритмом аутентифицированного шифрования в конверте формата `ispf1.<keyId>....` (при плановой смене мастер-ключа поддерживается перешифрование через `ISPF_SECURITY_SECRETS_PREVIOUS_KEY` и метод `POST /api/v1/federation/secrets-key/reencrypt` — см. [ADR-0065](decisions/0065-secrets-key-rotation.md)).

---

## Пошаговое выполнение

### Шаг 1. Сохраните учётные данные (`PUT /api/v1/credentials/by-path`)

```bash
curl -s -X PUT "$BASE/api/v1/credentials/by-path?path=root.platform.credentials.openai-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "kind": "openai",
    "secret": "sk-ЗАМЕНИТЕ-НА-КЛЮЧ",
    "metadata": {
      "baseUrl": "https://api.openai.com/v1",
      "model": "gpt-4o-mini"
    }
  }' | jq .
```

### Шаг 2. Убедитесь, что секрет не возвращается в открытом виде при чтении

```bash
curl -s "$BASE/api/v1/credentials/by-path?path=root.platform.credentials.openai-lab" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Ответ вернёт метаданные и признак `"hasSecret": true`, но никогда не выдаст само поле `secret` наружу.

### Шаг 3. Укажите путь к учётным данным в шаге BPMN

В атрибутах шага `llm_complete` или `invoke_agent` укажите путь к созданному узлу:

```xml
ispf:modelRef="root.platform.credentials.openai-lab"
```

**Следующий урок:** [Урок 6 — Инструменты анализа временных рядов (`Analytics AI`)](tutorial-ot-analytics-ai.md).
