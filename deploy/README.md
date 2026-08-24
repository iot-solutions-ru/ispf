# Deploy layout

**In git (universal):** production rollout, Helm, docker-compose templates, nginx prod/cluster, air-gap, driver interop CI, marketplace publish tooling.

**Local only (`deploy/local/`, gitignored):** lab cluster, load/stress gates, SSH helpers, soak tests, ad-hoc debug — see [`local/README.example.md`](local/README.example.md).

**Documented lab templates (in git):** [`examples/lab-mqtt-historian-stress/`](../examples/lab-mqtt-historian-stress/) — anonymized compose/env/scripts for MQTT historian benchmarks. Copy to `~/ispf` on lab hosts and fill real hosts in untracked `deploy/lab-*.env`.

## Universal entry points

| Script | Purpose |
|--------|---------|
| [`apply-platform-update.sh`](apply-platform-update.sh) | Apply staged jar + UI on a server |
| [`prod-quickstart.sh`](prod-quickstart.sh) | Single-node prod stack |
| [`cluster-quickstart.sh`](cluster-quickstart.sh) | Multi-replica cluster compose |
| [`health-check.sh`](health-check.sh) | Post-deploy smoke |
| [`vps-hardening.sh`](vps-hardening.sh) | Disk guardian, ClickHouse log TTL, journald limits (VPS) |
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

`docker-compose.vps-cluster.yml` additionally requires `NATS_USER` and `NATS_PASSWORD` (exported or in `.env` next to the compose file): the host-network NATS container binds loopback only and refuses to start without credentials, and the replicas connect with the same pair via `ISPF_NATS_URL`. Keep the credentials URL-safe — they are embedded in `nats://user:pass@127.0.0.1:4222`.

Both `docker-compose.vps-*.yml` run the JVM containers as uid/gid `10001` (same as the all-in-one Dockerfile image); the host data dir must be writable by it: `sudo chown -R 10001:10001 /opt/ispf/data`.

`apply-platform-update.sh` requires `CHECKSUMS.sha256` next to the staged artifacts (generated on the build side with `sha256sum ispf-server.jar web-console.zip driver-packs.tar.gz > CHECKSUMS.sha256`); the update aborts before install when checksums are missing or mismatched.

## VPS disk protection

ClickHouse `system.*` logs can fill the root disk on long-running VPS nodes. After deploy:

```bash
sudo bash deploy/vps-hardening.sh
```

This installs `disk-guardian.sh` (cron every 15 min), journald cap 300M, Docker log rotation, and ClickHouse log TTL config (`deploy/clickhouse-config/system-logs-production.xml`).

For Postgres/Redis on VPS, use external volumes when recreating containers:

```bash
docker compose -f deploy/docker-compose.postgres.yml \
  -f deploy/docker-compose.postgres.vps.yml up -d
```

Monitor: `/opt/ispf/data/disk-guardian.log`

## Nginx + hosted UI packs (ADR-0054)

On VPS split deploy (`web-console` from disk, JVM on `:8080`), **always** proxy `/apps/` to the server — never let the console SPA fallback handle it:

| Path | Handler |
|------|---------|
| `/api/`, `/ws/` | `proxy_pass` → `:8080` |
| `/apps/` | `proxy_pass` → `:8080` (`HostedUiPackFilter`) |
| `/`, `/assets/` | static `web-console` |

Templates already include `location ^~ /apps/` in [`nginx-ispf.conf`](nginx-ispf.conf) / [`nginx-vps-ssl.conf`](nginx-vps-ssl.conf). After changing nginx: `curl -fsS https://<host>/apps/<appId>/` must not return `<title>ISPF Admin Console</title>`. Details: [ADR-0054](../docs/en/decisions/0054-hosted-ui-packs.md).
