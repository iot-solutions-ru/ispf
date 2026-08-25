# License Builder (ISPF commercial bundle)

Инструменты поставщика для RSA-лицензий commercial bundle. Спецификация: [docs/en/decisions/0003-commercial-bundle-licensing.md](../../docs/en/decisions/0003-commercial-bundle-licensing.md).

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
# Prefer when server deploy verifies the raw JSON map (post contentSha256 DTO fix):
python tools/license-builder/sign-bundle.py \
  --bundle examples/mes-platform-production/bundle.json \
  --bundle-id mes-platform-production \
  --installation-id <hex-from-step-2> \
  --private-key tools/license-builder/keys/license-private.pem \
  --out /tmp/mes-platform-production-signed.json

# On ISPF ≤0.9.186 (hash over BundleManifest DTO), use the jar helper instead:
ISPF_SERVER_JAR=/opt/ispf/ispf-server.jar bash tools/license-builder/sign-bundle-via-jar.sh \
  --bundle examples/mes-platform-production/bundle.json \
  --bundle-id mes-platform-production \
  --installation-id <hex-from-step-2> \
  --private-key /opt/ispf/keys/license-private.pem \
  --out /tmp/mes-platform-production-signed.json
```

Demostand one-shot (generate keys + require-signed + smoke):

```bash
bash deploy/tools/vps-enable-signed-bundles.sh
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
