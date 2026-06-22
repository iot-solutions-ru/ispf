# Commercial bundle licensing

RSA-лицензирование commercial bundle при deploy. Архитектурное решение: [ADR-0010](decisions/0010-commercial-bundle-licensing.md).

## Принцип

| Слой | Лицензия |
|------|----------|
| Ядро ISPF (`main`) | Apache 2.0, без DRM |
| Commercial bundle | Optional секция `license` в manifest; verify при deploy |

## Конфигурация сервера

```yaml
ispf:
  license:
    data-dir: ${ISPF_DATA_DIR:./data}
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

| Переменная | Назначение |
|------------|------------|
| `ISPF_DATA_DIR` | Каталог для `.ispf-installation-id` |
| `ISPF_LICENSE_PUBLIC_KEY_PEM` | PEM публичного RSA-ключа поставщика |
| `ISPF_LICENSE_ENFORCE` | `true` — invalid license → HTTP 403 |

## Installation ID

Файл `{data-dir}/.ispf-installation-id` создаётся при первом старте.

```http
GET /api/v1/platform/installation-id
```

Admin передаёт `installationId` поставщику для выпуска лицензии.

## Формат `license` в bundle

```json
"license": {
  "bundleId": "mes-reference",
  "minPlatformVersion": "0.7.0",
  "installationId": "<hex>",
  "contentSha256": "<sha256 canonical manifest without license>",
  "expiresAt": "2027-12-31T23:59:59Z",
  "signature": "<base64 RSA-SHA256 over canonical claims JSON>"
}
```

`contentSha256` — SHA-256 от manifest **без** поля `license` (canonical JSON, sorted keys).

## Поставщик

CLI: [tools/license-builder/README.md](../tools/license-builder/README.md).

## Поведение deploy

| Условие | Результат |
|---------|-----------|
| Нет `license` | Deploy как раньше |
| `license` + `enforce=false` | WARN при ошибке, deploy продолжается |
| `license` + `enforce=true` + invalid | HTTP 403 |

## Связанные документы

- [PLUGINS.md](PLUGINS.md)
- [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md)
