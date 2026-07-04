#!/usr/bin/env bash
set -euo pipefail
curl -s http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | head -c 120
echo
python3 /opt/ispf/loadtest/setup-single-mqtt-historian.py
