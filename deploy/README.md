# Deploy layout

**In git (universal):** production rollout, Helm, docker-compose templates, nginx prod/cluster, air-gap, driver interop CI, marketplace publish tooling.

**Local only (`deploy/local/`, gitignored):** lab cluster, load/stress gates, SSH helpers, soak tests, ad-hoc debug — see [`local/README.example.md`](local/README.example.md).

## Universal entry points

| Script | Purpose |
|--------|---------|
| [`apply-platform-update.sh`](apply-platform-update.sh) | Apply staged jar + UI on a server |
| [`prod-quickstart.sh`](prod-quickstart.sh) | Single-node prod stack |
| [`cluster-quickstart.sh`](cluster-quickstart.sh) | Multi-replica cluster compose |
| [`health-check.sh`](health-check.sh) | Post-deploy smoke |
| [`air-gap-pack.sh`](air-gap-pack.sh) / [`air-gap-apply.sh`](air-gap-apply.sh) | Offline bundle |

## Universal `deploy/tools/`

| Tool | Purpose |
|------|---------|
| `driver-interop-smoke.sh` / `driver-interop-report.sh` | CI driver matrix |
| `publish-marketplace-*.sh` / `*.ps1` | Marketplace catalog/pack publish |
| `marketplace-generate-seed-listings.py` | Seed listing JSON |

## VPS / lab ops (gitignored at repo root)

Patterns in [`.gitignore`](../.gitignore): `deploy/vps-*`, `deploy/lab-*`, `deploy/run_lab_*`, `deploy/local/`, `deploy/tmp_*`, …

Copy `deploy/local/README.example.md` → `deploy/local/README.md` on first clone if you run lab gates.

## Environment

Secrets: `.env` (see [`.env.example`](../.env.example)). Never commit passwords or `deploy/lab_ssh.py`.
