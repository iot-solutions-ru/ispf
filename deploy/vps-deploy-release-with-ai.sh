#!/bin/bash
set -euo pipefail
VERSION="${1:-0.7.4}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export ISPF_AI_PROVIDER="${ISPF_AI_PROVIDER:-openai-compatible}"
export ISPF_AI_BASE_URL="${ISPF_AI_BASE_URL:-http://84.42.21.226:8000/v1}"
export ISPF_AI_MODEL="${ISPF_AI_MODEL:-unsloth/Qwen3.6-35B-A3B-NVFP4}"
export ISPF_AI_API_KEY="${ISPF_AI_API_KEY:?Set ISPF_AI_API_KEY before running}"
export ISPF_AI_TIMEOUT_SECONDS="${ISPF_AI_TIMEOUT_SECONDS:-180}"

bash "$SCRIPT_DIR/vps-configure-ai-env.sh"
bash "$SCRIPT_DIR/vps-apply-release.sh" "$VERSION"
systemctl restart ispf-server
sleep 20
curl -sf http://127.0.0.1:8080/actuator/health
echo
curl -sf http://127.0.0.1:8080/api/v1/ai/provider || true
echo
