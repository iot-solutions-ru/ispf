#!/usr/bin/env bash
# Start ispf-server replica-4 on LAN peer (192.168.100.10) pointing at lab shared services.
set -euo pipefail

LAB_HOST="${1:?lab host LAN IP}"
JAR="${2:-$HOME/ispf-cluster/ispf-server.jar}"
DRIVERS="${3:-$HOME/ispf-cluster/drivers}"

mkdir -p "$(dirname "$JAR")" "$DRIVERS"

docker rm -f ispf-replica-4 2>/dev/null || true
docker run -d --name ispf-replica-4 --restart unless-stopped \
  -e ISPF_DB_URL="jdbc:postgresql://${LAB_HOST}:5433/ispf" \
  -e ISPF_DB_USER=ispf \
  -e ISPF_DB_PASSWORD=ispf-cluster-lab \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${LAB_HOST}:5433/ispf" \
  -e SPRING_DATASOURCE_USERNAME=ispf \
  -e SPRING_DATASOURCE_PASSWORD=ispf-cluster-lab \
  -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
  -e ISPF_SERVER_PORT=8080 \
  -e ISPF_REPLICA_ID=replica-4 \
  -e ISPF_CLUSTER_ENABLED=true \
  -e ISPF_NATS_ENABLED=true \
  -e ISPF_NATS_URL="nats://${LAB_HOST}:4223" \
  -e ISPF_NATS_REPLICA_EVENTS=true \
  -e ISPF_REDIS_ENABLED=true \
  -e ISPF_REDIS_HOST="${LAB_HOST}" \
  -e ISPF_REDIS_PORT=6380 \
  -e ISPF_ENVIRONMENT=lab-cluster \
  -e ISPF_BOOTSTRAP_FIXTURES_ENABLED=false \
  -e ISPF_SECURITY_RBAC_ENABLED=true \
  -e ISPF_SECURITY_TOKEN_AUTH_ENABLED=true \
  -e ISPF_DRIVER_PACKS_DIR=/opt/ispf/drivers \
  -e ISPF_AI_PROVIDER=noop \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JAVA_TOOL_OPTIONS="-Xms256m -Xmx1536m" \
  -v "${JAR}:/opt/ispf/ispf-server.jar:ro" \
  -v "${DRIVERS}:/opt/ispf/drivers:ro" \
  -p 8084:8080 \
  eclipse-temurin:25-jre-jammy \
  java -jar /opt/ispf/ispf-server.jar

echo "==> Wait replica-4 health"
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8084/api/v1/info >/dev/null 2>&1; then
    curl -sf http://127.0.0.1:8084/api/v1/info | python3 -c \
      "import json,sys; d=json.load(sys.stdin); print('replica-4', d.get('replicaId'), d.get('version'), 'cluster', d.get('clusterEnabled'))"
    exit 0
  fi
  sleep 3
done
echo "ERROR: replica-4 not ready" >&2
docker logs ispf-replica-4 2>&1 | tail -30
exit 1
