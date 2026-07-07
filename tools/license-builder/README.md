# License Builder (ISPF commercial bundle)

Инструменты поставщика для RSA-лицензий commercial bundle. Спецификация: [docs/decisions/0003-commercial-bundle-licensing.md](../../docs/decisions/0003-commercial-bundle-licensing.md).

## Требования

- Python 3.10+
- `pip install cryptography`

## Workflow

1. **Сгенерировать ключи** (один раз у поставщика):

```bash
python tools/license-builder/generate-keys.py --out-dir tools/license-builder/keys
```

- `license-private.pem` — хранить у поставщика, не коммитить
- `license-public.pem` — на сервере: `ISPF_LICENSE_PUBLIC_KEY_PEM` или `ispf.license.public-key-pem`

2. **Получить installation ID** с целевого сервера (admin):

```http
GET /api/v1/platform/installation-id
```

3. **Подписать bundle** (manifest без секции `license`):

```bash
python tools/license-builder/sign-bundle.py \
  --bundle examples/mes-reference/bundle.json \
  --bundle-id mes-reference \
  --installation-id <hex-from-step-2> \
  --private-key tools/license-builder/keys/license-private.pem
```

4. **Включить проверку на сервере** (production):

```bash
export ISPF_LICENSE_ENFORCE=true
export ISPF_LICENSE_PUBLIC_KEY_PEM="$(cat license-public.pem)"
```

5. **Deploy** как обычно: `POST /api/v1/applications/{appId}/deploy`

## Примечания

- Bundle **без** `license` — Apache reference apps, deploy без изменений.
- При `enforce=false` (local/dev) ошибки лицензии — только WARN в логе.
