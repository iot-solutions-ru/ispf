#!/usr/bin/env bash
# aarch64 lab stand: Azure SQL Edge + ISPF bootJar (B1 fences). Does not touch PG lab-cluster.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

NAME_SQL=${NAME_SQL:-ispf-mssql-b1-stand}
NAME_APP=${NAME_APP:-ispf-mssql-b1-app}
NET=${NET:-ispf-mssql-b1-net}
: "${MSSQL_SA_PASSWORD:?set MSSQL_SA_PASSWORD (lab throwaway)}"
JAR=${JAR:-/home/iot-solutions/ispf-mssql-b1/ispf-server.jar}
ISPF_PORT=${ISPF_PORT:-18080}
BIND_HOST=${BIND_HOST:-127.0.0.1}
IMAGE_SQL=${IMAGE_SQL:-mcr.microsoft.com/azure-sql-edge:latest}
IMAGE_JRE=${IMAGE_JRE:-eclipse-temurin:25-jre-jammy}
BUNDLE=${BUNDLE:-"$SCRIPT_DIR/mssql-b1-cert-bundle.json"}
ROUNDS=${ROUNDS:-15}
CONCURRENCY=${CONCURRENCY:-4}

docker network create "$NET" 2>/dev/null || true
docker rm -f "$NAME_SQL" "$NAME_APP" 2>/dev/null || true

echo "== start Azure SQL Edge =="
docker run -d --name "$NAME_SQL" --hostname "$NAME_SQL" --network "$NET" \
  --restart=no \
  -e ACCEPT_EULA=1 \
  -e "MSSQL_SA_PASSWORD=$MSSQL_SA_PASSWORD" \
  -e MSSQL_PID=Developer \
  -e MSSQL_MEMORY_LIMIT_MB=2048 \
  -p "${BIND_HOST}:14333:1433" \
  "$IMAGE_SQL"

sql() {
  docker run --rm --network host --platform linux/amd64 \
    --entrypoint /opt/mssql-tools18/bin/sqlcmd \
    mcr.microsoft.com/mssql/server:2022-latest \
    -S 127.0.0.1,14333 -U sa -P "$MSSQL_SA_PASSWORD" -C -Q "$1"
}

echo "== wait for SQL accept =="
ready=0
for i in $(seq 1 120); do
  if sql "SELECT 1" &>/dev/null; then
    echo "sql_ready attempt=$i"
    ready=1
    break
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx "$NAME_SQL"; then
    echo "SQL container exited" >&2
    docker logs "$NAME_SQL" 2>&1 | tail -40
    exit 1
  fi
  sleep 5
done
[[ "$ready" -eq 1 ]] || { echo "SQL never accepted logins" >&2; exit 1; }

echo "== prepare ispf DB =="
sql "IF DB_ID(N'ispf') IS NULL CREATE DATABASE [ispf] COLLATE Latin1_General_CS_AS"
sql "ALTER DATABASE [ispf] SET ALLOW_SNAPSHOT_ISOLATION ON"
sql "ALTER DATABASE [ispf] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE"
sql "SELECT name, collation_name, is_read_committed_snapshot_on FROM sys.databases WHERE name=N'ispf'"

[[ -f "$JAR" ]] || { echo "missing jar $JAR (set JAR=)" >&2; exit 2; }

JDBC="jdbc:sqlserver://${NAME_SQL}:1433;databaseName=ispf;encrypt=false;trustServerCertificate=true"
echo "== start ISPF =="
docker run -d --name "$NAME_APP" --network "$NET" \
  -p "${BIND_HOST}:${ISPF_PORT}:8080" \
  -v "$JAR:/app/ispf-server.jar:ro" \
  -e SPRING_PROFILES_ACTIVE=local \
  -e ISPF_METADATA_DB_KIND=mssql \
  -e SPRING_DATASOURCE_URL="$JDBC" \
  -e SPRING_DATASOURCE_USERNAME=sa \
  -e SPRING_DATASOURCE_PASSWORD="$MSSQL_SA_PASSWORD" \
  -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.microsoft.sqlserver.jdbc.SQLServerDriver \
  -e ISPF_TENANT_DB_ROW_ISOLATION=false \
  -e ISPF_TENANT_ISOLATION_MODE=logical \
  -e ISPF_VARIABLE_HISTORY_ENABLED=false \
  -e ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false \
  -e ISPF_EVENT_JOURNAL_ENABLED=false \
  -e ISPF_CLUSTER_ENABLED=false \
  -e ISPF_NATS_ENABLED=false \
  -e ISPF_REDIS_ENABLED=false \
  -e ISPF_SECURITY_STRICT_STARTUP=false \
  -e ISPF_BOOTSTRAP_FIXTURES_ENABLED=false \
  -e ISPF_LICENSE_ENFORCE=false \
  "$IMAGE_JRE" \
  java -jar /app/ispf-server.jar

echo "== wait ISPF =="
for i in $(seq 1 90); do
  if curl -sf "http://127.0.0.1:${ISPF_PORT}/actuator/health" | grep -q UP; then
    echo "ispf_up attempt=$i"
    break
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx "$NAME_APP"; then
    docker logs "$NAME_APP" 2>&1 | tail -80
    exit 1
  fi
  sleep 5
  [[ $i -eq 90 ]] && { docker logs "$NAME_APP" 2>&1 | tail -80; exit 1; }
done

echo "== load soak =="
BASE="http://127.0.0.1:${ISPF_PORT}" ROUNDS="$ROUNDS" CONCURRENCY="$CONCURRENCY" BUNDLE="$BUNDLE" \
  bash "$SCRIPT_DIR/load.sh"

echo "== fence (cluster must refuse) =="
docker rm -f ispf-mssql-b1-fence 2>/dev/null || true
docker run -d --name ispf-mssql-b1-fence --network "$NET" \
  -v "$JAR:/app/ispf-server.jar:ro" \
  -e SPRING_PROFILES_ACTIVE=local \
  -e ISPF_METADATA_DB_KIND=mssql \
  -e SPRING_DATASOURCE_URL="$JDBC" \
  -e SPRING_DATASOURCE_USERNAME=sa \
  -e SPRING_DATASOURCE_PASSWORD="$MSSQL_SA_PASSWORD" \
  -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.microsoft.sqlserver.jdbc.SQLServerDriver \
  -e ISPF_TENANT_DB_ROW_ISOLATION=false \
  -e ISPF_VARIABLE_HISTORY_ENABLED=false \
  -e ISPF_EVENT_JOURNAL_ENABLED=false \
  -e ISPF_CLUSTER_ENABLED=true \
  -e ISPF_BOOTSTRAP_FIXTURES_ENABLED=false \
  "$IMAGE_JRE" java -jar /app/ispf-server.jar >/dev/null

fence_ok=0
for i in $(seq 1 40); do
  if docker logs ispf-mssql-b1-fence 2>&1 | grep -q 'not certified on MSSQL'; then
    fence_ok=1
    break
  fi
  sleep 2
done
exit_code=$(docker inspect -f '{{.State.ExitCode}}' ispf-mssql-b1-fence 2>/dev/null || echo 1)
docker rm -f ispf-mssql-b1-fence >/dev/null 2>&1 || true
if [[ "$fence_ok" -ne 1 ]]; then
  echo "fence_missing" >&2
  exit 1
fi
echo "fence_ok exit_code=$exit_code"
echo "LAB_MSSQL_B1_OK"
