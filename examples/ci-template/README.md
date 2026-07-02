# ISPF bundle CI template (BL-98)

Copy this folder into your solution repository and adjust `bundle.json` path and secrets.

## Prerequisites

- Running ISPF instance (local `bootRun` or staging URL)
- Admin API token or local dev auth headers

## Environment

| Variable | Description |
|----------|-------------|
| `ISPF_BASE_URL` | e.g. `http://localhost:8080` |
| `ISPF_API_TOKEN` | Bearer token (optional in local profile with role header) |

## Local validate

```bash
export ISPF_BASE_URL=http://localhost:8080
node ../../tools/bundle-validate-cli/validate.mjs examples/warehouse-app/bundle.json warehouse
```

Or curl:

```bash
curl -sS -X POST "$ISPF_BASE_URL/api/v1/applications/warehouse/bundle/validate?dryRun=true" \
  -H "Content-Type: application/json" \
  --data-binary @examples/warehouse-app/bundle.json
```

## GitHub Actions

See [`.github/workflows/bundle-ci.yml`](.github/workflows/bundle-ci.yml).

Wire `ISPF_BASE_URL` and `ISPF_API_TOKEN` as repository secrets for staging smoke.
