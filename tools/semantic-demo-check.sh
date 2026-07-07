#!/usr/bin/env bash
# BL-105: smoke check for semantic roundtrip APIs (requires running server; use gradle test for CI).
set -euo pipefail

BASE_URL="${ISPF_BASE_URL:-http://localhost:8080}"
DEVICE="root.platform.devices.lab-userA-01"

echo "== Haystack export =="
curl -sf "${BASE_URL}/api/v1/platform/haystack/export?rootPath=${DEVICE}&includePoints=true" | head -c 400
echo

echo "== Haystack query =="
curl -sf "${BASE_URL}/api/v1/platform/haystack/query?filter=point%20and%20temp&rootPath=${DEVICE}&entityKind=point" | head -c 400
echo

echo "== Brick infer =="
curl -sf "${BASE_URL}/api/v1/platform/brick/infer?objectPath=${DEVICE}" | head -c 400
echo

echo "== Brick export (jsonld) =="
curl -sf "${BASE_URL}/api/v1/platform/brick/export?rootPath=${DEVICE}&format=jsonld&includePoints=true" | head -c 400
echo

echo "OK: semantic demo smoke passed"
