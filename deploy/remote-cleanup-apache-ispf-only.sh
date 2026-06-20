#!/bin/bash
set -euo pipefail

echo "=== Stopping interfering services ==="
for svc in apache2 ihttpd mysql php-fpm74 php8.3-fpm proftpd dovecot exim4 named; do
  systemctl stop "$svc" 2>/dev/null || true
  systemctl disable "$svc" 2>/dev/null || true
done

pkill -f '/usr/local/mgr5/sbin/ihttpd' 2>/dev/null || true
pkill -f 'bin/core ispmgr' 2>/dev/null || true
pkill -f 'bin/core core' 2>/dev/null || true

docker stop $(docker ps -q) 2>/dev/null || true
systemctl stop docker containerd 2>/dev/null || true
systemctl disable docker 2>/dev/null || true

echo "=== Removing Apache and ISPmanager Apache modules ==="
export DEBIAN_FRONTEND=noninteractive
apt-get purge -y \
  apache2 apache2-bin apache2-data apache2-utils \
  libapache2-mod-authnz-external libapache2-mod-php libapache2-mod-php8.3 \
  libapache2-mod-rpaf libapache2-mpm-itk \
  isp-php56-mod-apache isp-php74-mod-apache isp-php83-mod-apache \
  ispmanager-pkg-httpd-itk pwauth 2>/dev/null || true
apt-get autoremove -y -qq
apt-get autoclean -y -qq

echo "=== ISPF backend on port 8080 ==="
sed -i 's/ISPF_SERVER_PORT=18080/ISPF_SERVER_PORT=8080/' /etc/systemd/system/ispf-server.service

cat > /etc/nginx/nginx.conf << 'NGINX_MAIN'
user www-data;
worker_processes auto;
pid /run/nginx.pid;
error_log /var/log/nginx/error.log;

events {
    worker_connections 1024;
}

http {
    sendfile on;
    tcp_nopush on;
    types_hash_max_size 2048;
    server_tokens off;

    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    access_log /var/log/nginx/access.log;
    client_max_body_size 128m;

    include /etc/nginx/conf.d/ispf.conf;
}
NGINX_MAIN

cat > /etc/nginx/conf.d/ispf.conf << 'NGINX_ISPF'
server {
    listen 80;
    listen [::]:80;
    server_name ai.iot-solutions.ru _;

    root /opt/ispf/web-console;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }

    location /actuator/ {
        proxy_pass http://127.0.0.1:8080;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINX_ISPF

rm -f /etc/nginx/conf.d/ssl_servers_inc.conf
rm -f /etc/nginx/sites-enabled/*
rm -rf /etc/nginx/vhosts-includes 2>/dev/null || true

nginx -t
systemctl enable nginx
systemctl restart nginx

systemctl daemon-reload
systemctl restart ispf-server

sleep 15

echo "=== Verification ==="
systemctl is-active ispf-server
systemctl is-active nginx
systemctl is-active apache2 2>/dev/null || echo "apache2: removed/stopped"
ss -tlnp | grep -E ':80 |:8080 ' || true
curl -sf http://127.0.0.1:8080/actuator/health
echo ""
curl -sf -o /dev/null -w "UI port 80: HTTP %{http_code}\n" http://127.0.0.1/
curl -sf http://127.0.0.1/api/v1/info | head -c 200
echo ""
free -h | head -2
