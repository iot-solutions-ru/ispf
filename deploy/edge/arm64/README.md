# ARM64 edge gateway (BL-187)

Lean **linux/arm64** compose profile for Raspberry Pi 4+ (4 GB RAM), Ampere, or industrial gateways behind NAT. Outbound federation tunnel is enabled; fixtures and cluster are off.

Canonical compose: [`docker-compose.yml`](docker-compose.yml). Legacy alias: [`../../docker-compose.edge-arm.yml`](../../docker-compose.edge-arm.yml).

See also [DEMOSTANDS.md](../../../docs/DEMOSTANDS.md) — edge column and [FEDERATION.md](../../../docs/FEDERATION.md) for hub pairing.

---

## Hardware baseline

| Item | Minimum | Recommended |
|------|---------|-------------|
| CPU | 4× ARM64 cores @ 1.5 GHz | Pi 5 / Ampere Altra edge |
| RAM | 4 GB | 8 GB |
| Storage | 16 GB SD / eMMC | 64 GB SSD |
| Network | Outbound HTTPS to hub | Static route or VPN to OT VLAN |

---

## Prerequisites

- Docker Engine 24+ with `linux/arm64` (native Pi OS 64-bit or Ubuntu Server arm64).
- Built artifacts staged under `deploy/edge/arm64/artifacts/` (see quick start in compose header).
- Driver packs copied to `artifacts/drivers/` (permissive profile only unless legal review complete).

---

## Quick start

From repository root:

```bash
./gradlew :packages:ispf-server:bootJar -x test
cd apps/web-console && npm ci && npm run build && cd ../..

mkdir -p deploy/edge/arm64/artifacts/drivers
cp packages/ispf-server/build/libs/ispf-server-*.jar deploy/edge/arm64/artifacts/ispf-server.jar
cp -r apps/web-console/dist/* deploy/edge/arm64/artifacts/web-console/

docker compose -f deploy/edge/arm64/docker-compose.yml up -d
```

Open **http://127.0.0.1:8081** (nginx → API proxy). Direct API: **http://127.0.0.1:8080/api/v1/info**.

Override artifact paths:

```bash
export ISPF_SERVER_JAR=/opt/ispf/staging/ispf-server.jar
export ISPF_WEB_CONSOLE=/opt/ispf/staging/web-console
export ISPF_DRIVER_PACKS=/opt/ispf/drivers
docker compose -f deploy/edge/arm64/docker-compose.yml up -d
```

---

## Federation (NAT egress)

| Variable | Default | Purpose |
|----------|---------|---------|
| `ISPF_FEDERATION_OUTBOUND_ENABLED` | `true` | Edge initiates tunnel to hub (no inbound port forward) |
| `ISPF_REPLICA_ID` | `edge-1` | Peer id on hub |
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | `false` | Prod policy — no demo fixtures |

Register the edge peer on the central hub ([FEDERATION.md](../../../docs/FEDERATION.md)), then verify `GET /api/v1/federation/peers` from hub shows `edge-1` healthy.

---

## Resource profile

| Service | Memory limit | Notes |
|---------|--------------|-------|
| postgres | 512 MB | Timescale PG 16 |
| redis | 128 MB | Session/cache |
| ispf-server | 512 MB | JVM `-Xmx384m` |
| nginx | 64 MB | Static UI + `/api` proxy |

**Do not enable on edge:** cluster, elastic ingress workers, ClickHouse, demo fixtures. See [DEMOSTANDS.md](../../../docs/DEMOSTANDS.md) edge tuning table.

---

## Validation script

From repository root (static checks always; runtime smoke when artifacts are staged):

```bash
bash deploy/edge/arm64/validate.sh
```

Set `ISPF_EDGE_VALIDATE_SKIP_SMOKE=1` to skip `docker compose up` (CI without staged JAR/UI).

---

## Verification checklist

1. `curl -s http://127.0.0.1:8080/api/v1/info` — single replica, `clusterEnabled=false`.
2. Operator UI loads at `:8081` without admin console errors.
3. After hub pairing — remote variables readable from central federation catalog.
4. Steady-state CPU &lt; 15% at configured poll interval (`GET /api/v1/platform/metrics`).

---

## Helm equivalent

For Kubernetes arm64 nodes, set `edge.enabled=true` and `edge.arm64=true` in [`deploy/helm/ispf/values.yaml`](../../helm/ispf/values.yaml) and tune `ispf.server.javaOpts` to match this profile.

---

## Related

- [DEPLOYMENT.md](../../../docs/DEPLOYMENT.md) — production deploy paths
- [ROADMAP_PHASE25.md](../../../docs/ROADMAP_PHASE25.md) — BL-187
