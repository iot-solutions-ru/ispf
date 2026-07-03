# Commercial bundle licensing

RSA-лицензирование commercial bundle при deploy. Архитектурное решение: [0003](decisions/0003-commercial-bundle-licensing.md).

## Принцип

| Слой | Лицензия |
|------|----------|
| Platform (`ispf-server`, web-console) | **GNU AGPL v3** (+ optional [LICENSE-COMMERCIAL.md](../LICENSE-COMMERCIAL.md)) |
| Device driver pack | `licenseType` per pack — see [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md) |
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
| `ISPF_LICENSE_PUBLIC_KEY_PEM` | PEM публичного RSA-ключа поставщика (можно несколько PEM-блоков подряд для ротации) |
| `ISPF_LICENSE_ENFORCE` | `true` — invalid bundle/driver/platform license блокирует deploy / pack load / **старт сервера** |

## Installation ID

Файл `{data-dir}/.ispf-installation-id` создаётся при первом старте.

```http
GET /api/v1/platform/installation-id
```

Admin передаёт `installationId` поставщику для выпуска лицензии.

Статус лицензии (admin): `GET /api/v1/platform/license` — mode, tier, valid, enforce, installationId. Карточка в Web Console: **System → Metrics**.

## Platform license file (`platform-license.json`)

Файл `{data-dir}/platform-license.json` — Enterprise exemption от AGPL (см. [LICENSE-COMMERCIAL.md](../LICENSE-COMMERCIAL.md)).

| Условие | Результат |
|---------|-----------|
| Файл отсутствует | Community (AGPL), старт разрешён |
| Файл + valid | Commercial tier active |
| Файл + invalid + `enforce=false` | WARN в логе, старт разрешён |
| Файл + invalid + `enforce=true` | **Сервер не стартует** (`IllegalStateException`) |

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
| Нет `license` | Deploy как раньше (если `require-signed-bundles=false`) |
| Нет `license` + `require-signed-bundles=true` | HTTP 403 ([BL-100](EXCELLENCE_BACKLOG.md#bl-100--bundle-trust-signing-optional)) |
| `license` + `enforce=false` + invalid | WARN при ошибке, deploy продолжается (кроме `require-signed-bundles=true` → 403) |
| `license` + (`enforce=true` **или** `require-signed-bundles=true`) + invalid | HTTP 403 |

Property: `ispf.license.require-signed-bundles` / env `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES`. См. [DEPLOYMENT.md § Bundle signing](DEPLOYMENT.md#bundle-signing-bl-100).

## Production key rotation (ops)

Ротация RSA-ключей поставщика **без** смены installation ID:

| Шаг | Действие |
|-----|----------|
| 1 | Сгенерировать новую пару ключей (`tools/license-builder/`); сохранить старый private key до конца grace period |
| 2 | На platform: задеплоить **оба** public key в `ISPF_LICENSE_PUBLIC_KEY_PEM` (несколько `-----BEGIN PUBLIC KEY-----` блоков в одной переменной); подпись принимается, если совпала с любым ключом |
| 3 | Перевыпустить commercial bundle / driver pack signatures для активных клиентов |
| 4 | Grace period (рекомендуется ≥30 дней): старые подписи ещё принимаются только если public key не меняли; после замены ключа старые лицензии **невалидны** — планировать окно обслуживания |
| 5 | `enforce=true` на staging до prod; мониторить WARN/403 в deploy logs |
| 6 | Уничтожить старый private key после подтверждения, что все установки на новых лицензиях |

Installation ID (`GET /api/v1/platform/installation-id`) при ротации **не меняется**. Licensed driver packs используют тот же `ispf.license.public-key-pem` — см. [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md).

## Связанные документы

- [PLUGINS.md](PLUGINS.md)
- [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md)
