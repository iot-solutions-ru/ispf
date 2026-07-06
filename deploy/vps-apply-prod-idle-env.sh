#!/usr/bin/env bash
# Merge deploy/ispf-server.prod-idle.env into /opt/ispf/ispf-server.env and restart single-node stack.
set -eu

ISPFDIR="${ISPFDIR:-/opt/ispf}"
ENV_FILE="${ISPFDIR}/ispf-server.env"
PROD_IDLE="${1:-${ISPFDIR}/ispf-server.prod-idle.env}"
BACKUP="${ENV_FILE}.bak.$(date +%Y%m%d%H%M%S)"

if [ ! -f "$PROD_IDLE" ]; then
  echo "ERROR: prod-idle env not found: $PROD_IDLE" >&2
  exit 1
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: env file not found: $ENV_FILE" >&2
  exit 1
fi

cp "$ENV_FILE" "$BACKUP"
echo "Backup: $BACKUP"

python3 - "$ENV_FILE" "$PROD_IDLE" <<'PY'
import sys
from pathlib import Path

target = Path(sys.argv[1])
overlay = Path(sys.argv[2])
lines = target.read_text(encoding="utf-8").splitlines()
keys = {}
order = []
for line in lines:
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in stripped:
        order.append(line)
        continue
    key, _, value = stripped.partition("=")
    key = key.strip()
    keys[key] = value
    if key not in [o.partition("=")[0].strip() for o in order if "=" in o]:
        order.append(line)

for raw in overlay.read_text(encoding="utf-8").splitlines():
    stripped = raw.strip()
    if not stripped or stripped.startswith("#") or "=" not in stripped:
        continue
    key, _, value = stripped.partition("=")
    key = key.strip()
    keys[key] = value
    replaced = False
    for i, line in enumerate(order):
        if "=" in line and line.split("=", 1)[0].strip() == key:
            order[i] = f"{key}={value}"
            replaced = True
            break
    if not replaced:
        order.append(f"{key}={value}")

# Force metrics probe off at boot (runtime toggle via Load diagnostics only)
keys["ISPF_PLATFORM_METRICS_PROBE_ENABLED"] = "false"
for i, line in enumerate(order):
    if line.startswith("ISPF_PLATFORM_METRICS_PROBE_ENABLED="):
        order[i] = "ISPF_PLATFORM_METRICS_PROBE_ENABLED=false"
        break
else:
    order.append("ISPF_PLATFORM_METRICS_PROBE_ENABLED=false")

target.write_text("\n".join(order) + "\n", encoding="utf-8")
print(f"Merged prod-idle overlay into {target}")
PY

recreate_ispf_container() {
  COMPOSE_FILE="${ISPFDIR}/docker-compose.vps-single.yml"
  if [ ! -f "$COMPOSE_FILE" ]; then
    docker restart ispf-vps-replica-1 2>/dev/null || true
    return
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose -f "$COMPOSE_FILE")
  else
    COMPOSE=(docker compose -f "$COMPOSE_FILE")
  fi
  "${COMPOSE[@]}" rm -sf ispf-server-1 2>/dev/null || true
  docker rm -f ispf-vps-replica-1 2>/dev/null || true
  if ! "${COMPOSE[@]}" up -d ispf-server-1; then
    echo "WARN: compose recreate failed; falling back to docker run" >&2
    docker run -d --name ispf-vps-replica-1 --network host --restart unless-stopped \
      -w /opt/ispf \
      -v "${ISPFDIR}/ispf-server.jar:/opt/ispf/ispf-server.jar:ro" \
      -v "${ISPFDIR}/data/drivers:/opt/ispf/drivers:ro" \
      -v "${ISPFDIR}/data:/opt/ispf/data" \
      --env-file "${ISPFDIR}/ispf-server.env" \
      -e ISPF_CLUSTER_ENABLED=false \
      -e ISPF_REPLICA_ID=replica-1 \
      -e ISPF_REPLICA_ROLE=all \
      -e ISPF_SERVER_PORT=8081 \
      -e ISPF_NATS_ENABLED=false \
      -e ISPF_REDIS_ENABLED=true \
      -e ISPF_REDIS_HOST=127.0.0.1 \
      -e ISPF_DRIVER_PACKS_DIR=/opt/ispf/drivers \
      -e 'JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC' \
      eclipse-temurin:25-jre-jammy \
      java -jar /opt/ispf/ispf-server.jar --spring.profiles.active=local
  fi
}

if docker ps -a --format '{{.Names}}' | grep -q '^ispf-vps-replica-1$'; then
  recreate_ispf_container
else
  systemctl restart ispf-server 2>/dev/null || recreate_ispf_container
fi

sleep 10
PORT="${ISPF_SERVER_PORT:-8081}"
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  if curl -sf "http://127.0.0.1:${PORT}/api/v1/info" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("version", d.get("version"))'; then
    break
  fi
  echo "waiting for ISPF (${i}/12)..."
  sleep 10
done
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' ispf-vps-replica-1 2>/dev/null || true
echo "Done. Metrics probe: enable only via Load diagnostics toggle in UI."
