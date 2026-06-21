#!/bin/bash
set -euo pipefail
cd /tmp
rm -rf ispf-build
git clone --depth 1 https://github.com/Michaael/IoT-Solutions-Platform.git ispf-build
cd ispf-build
chmod +x gradlew
./gradlew :packages:ispf-server:bootJar -x test --no-daemon
JAR="$(ls packages/ispf-server/build/libs/ispf-server-*.jar | grep -v plain | head -1)"
install -m 644 "$JAR" /opt/ispf/ispf-server.jar
systemctl restart ispf-server
for i in $(seq 1 90); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo HEALTH_OK
    break
  fi
  if [ "$i" -eq 90 ]; then
    journalctl -u ispf-server -n 60 --no-pager
    exit 1
  fi
  sleep 2
done
curl -sf http://127.0.0.1/api/v1/info
echo
docker-compose -f /opt/ispf/docker-compose.postgres.yml ps
