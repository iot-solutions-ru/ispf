# ISPF bundle CI template

Copy this workflow into a repository that contains the ISPF CLI (`tools/ispf-cli`) and adjust the bundle path and application id.

## Prerequisites

- Node.js 20 or newer
- A checkout that includes `tools/ispf-cli` and the bundle schema at `packages/ispf-server/src/main/resources/schema/bundle.schema.json` (`--local` reads that file)
- For the remote check: a running ISPF server and `ISPF_API_TOKEN`

## Environment

| Variable | Description |
|----------|-------------|
| `ISPF_BASE_URL` | e.g. `http://localhost:8080`. When empty, the workflow checks the bundle locally. |
| `ISPF_API_TOKEN` | Bearer token. `ispf login` writes the same token to `~/.ispf/credentials.json`. |

## Local validate

From the repository root, after `npm install` in `tools/ispf-cli`:

```bash
node tools/ispf-cli/bin/ispf.mjs validate examples/warehouse-app/bundle.json --local --strict
```

With a running server:

```bash
export ISPF_BASE_URL=http://localhost:8080
node tools/ispf-cli/bin/ispf.mjs login --username admin --password admin
node tools/ispf-cli/bin/ispf.mjs validate examples/warehouse-app/bundle.json --app warehouse --strict
```

`--strict` fails the check when application logic sits on a `DEVICE` object.

## GitHub Actions

See [`.github/workflows/bundle-ci.yml`](.github/workflows/bundle-ci.yml).

Without `ISPF_BASE_URL` the job still runs `ispf validate --local --strict`. With the secret set it also asks the server. Store `ISPF_API_TOKEN` as a repository secret for that remote check.

---

# Шаблон CI для бандла ISPF

Скопируйте этот workflow в репозиторий, где есть CLI ISPF (`tools/ispf-cli`), и поправьте путь к бандлу и идентификатор приложения.

## Что нужно

- Node.js 20 или новее
- Копия репозитория с `tools/ispf-cli` и схемой бандла `packages/ispf-server/src/main/resources/schema/bundle.schema.json` (её читает `--local`)
- Для удалённой проверки: запущенный сервер ISPF и `ISPF_API_TOKEN`

## Переменные

| Переменная | Описание |
|----------|-------------|
| `ISPF_BASE_URL` | Например `http://localhost:8080`. Если пусто, workflow проверяет бандл локально. |
| `ISPF_API_TOKEN` | Bearer-токен. `ispf login` пишет такой же токен в `~/.ispf/credentials.json`. |

## Локальная проверка

Из корня репозитория, после `npm install` в `tools/ispf-cli`:

```bash
node tools/ispf-cli/bin/ispf.mjs validate examples/warehouse-app/bundle.json --local --strict
```

С запущенным сервером:

```bash
export ISPF_BASE_URL=http://localhost:8080
node tools/ispf-cli/bin/ispf.mjs login --username admin --password admin
node tools/ispf-cli/bin/ispf.mjs validate examples/warehouse-app/bundle.json --app warehouse --strict
```

`--strict` не пропускает бандл, если прикладная логика лежит на объекте `DEVICE`.

## GitHub Actions

Смотрите [`.github/workflows/bundle-ci.yml`](.github/workflows/bundle-ci.yml).

Без `ISPF_BASE_URL` job всё равно выполняет `ispf validate --local --strict`. Если секрет задан, он дополнительно спрашивает сервер. Для этой удалённой проверки положите `ISPF_API_TOKEN` в секреты репозитория.
