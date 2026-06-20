#!/bin/bash
set -euo pipefail

echo "=== Stopping unnecessary services ==="
systemctl stop ouroboros-server 2>/dev/null || true
systemctl disable ouroboros-server 2>/dev/null || true
pkill -f "/root/Ouroboros/telegram_bridge" 2>/dev/null || true
pkill -f "/root/server.py" 2>/dev/null || true

if [ -d /root/searxng ]; then
  cd /root/searxng && docker-compose down 2>/dev/null || true
fi
docker stop searxng_searxng_1 searxng_redis_1 searxng 2>/dev/null || true

echo "=== Installing Java 25 ==="
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q "25"; then
  apt-get update -qq
  apt-get install -y -qq wget apt-transport-https gnupg curl
  install -d -m 0755 /etc/apt/keyrings
  curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb noble main" > /etc/apt/sources.list.d/adoptium.list
  apt-get update -qq
  apt-get install -y -qq temurin-25-jdk
fi
java -version

echo "=== Deploying ISPF to /opt/ispf ==="
mkdir -p /opt/ispf/data /opt/ispf/web-console
mv -f /tmp/ispf-server.jar /opt/ispf/ispf-server.jar
rm -rf /opt/ispf/web-console/*
mv /tmp/ispf-web-console-dist/* /opt/ispf/web-console/

cat > /etc/systemd/system/ispf-server.service << 'EOF'
[Unit]
Description=ISPF Platform Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/ispf
Environment=ISPF_SERVER_PORT=18080
ExecStart=/usr/bin/java -jar /opt/ispf/ispf-server.jar --spring.profiles.active=local
Restart=always
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/nginx/conf.d/ispf.conf << 'EOF'
server {
    listen 8090;
    listen [::]:8090;
    server_name _;

    root /opt/ispf/web-console;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:18080;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

nginx -t
systemctl reload nginx

systemctl daemon-reload
systemctl enable ispf-server
systemctl restart ispf-server

sleep 20
systemctl is-active ispf-server
curl -sf http://127.0.0.1:18080/actuator/health
echo ""
curl -sf -o /dev/null -w "Web UI HTTP %{http_code}\n" http://127.0.0.1:8090/
curl -sf http://127.0.0.1:8090/api/v1/info
echo ""
echo "=== Memory after cleanup ==="
free -h
ps aux --sort=-%mem | head -8
