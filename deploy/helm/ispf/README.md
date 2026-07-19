# ISPF Helm chart (BL-186)

Production-oriented Kubernetes chart for ISPF server + optional analytics replicas. Companion to Docker Compose / VPS paths in [deployment.md](../../../docs/en/deployment.md).

| Item | Value |
|------|-------|
| Chart path | `deploy/helm/ispf` |
| Chart version | see `Chart.yaml` |
| App version | see `Chart.yaml` `appVersion` |

---

## Prerequisites

- Kubernetes 1.27+
- Helm 3.14+
- Container images / staged JAR+UI (chart defaults mount Temurin + expect config/secrets for DB)

---

## Install (smoke)

```bash
# Lint + render (no cluster required)
bash deploy/helm/ispf/validate.sh

# Install into a namespace (cluster required)
helm upgrade --install ispf deploy/helm/ispf \
  --namespace ispf --create-namespace \
  --set secrets.dbPassword='change-me' \
  --set ispf.bootstrap.fixturesEnabled=false

# Smoke
kubectl -n ispf rollout status deploy/ispf --timeout=180s
kubectl -n ispf port-forward svc/ispf 8080:80 &
curl -sf http://127.0.0.1:8080/actuator/health
curl -sf http://127.0.0.1:8080/api/v1/info | head -c 400
```

Uninstall:

```bash
helm uninstall ispf -n ispf
```

---

## Common values

| Path | Purpose |
|------|---------|
| `ispf.cluster.enabled` | Multi-replica cluster (default `false`) |
| `ispf.historian.deployProfile` | `hot-only` / `three-tier` (BL-159) |
| `ispf.historian.warmEnabled` | ClickHouse warm tier |
| `analytics.enabled` | BL-207 analytics replica Deployment |
| `edge.enabled` / `edge.arm64` | Hints for ARM edge nodes (see [edge/arm64](../../edge/arm64/README.md)) |
| `ingress.enabled` | Expose via Ingress |

Full defaults: [`values.yaml`](values.yaml).

---

## Validation script

`validate.sh` runs `helm lint` + `helm template` (uses local `helm` or `alpine/helm` Docker image).

```bash
bash deploy/helm/ispf/validate.sh
```

CI should fail the PR if lint/template fails.

---

## Related

- [docs/en/deployment.md](../../../docs/en/deployment.md) — Helm section
- [docs/en/demostands.md](../../../docs/en/demostands.md) — prod / edge profiles
- [docs/en/roadmap.md](../../../docs/en/roadmap.md) — BL-186
