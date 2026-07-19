#!/usr/bin/env bash
# BL-186: helm lint + template smoke (no cluster required).
set -euo pipefail

CHART_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${CHART_DIR}/../../.." && pwd)"
REL_CHART="deploy/helm/ispf"

run_helm() {
  if command -v helm >/dev/null 2>&1; then
    helm "$@"
    return
  fi
  if command -v docker >/dev/null 2>&1; then
    docker run --rm -v "${ROOT}:/apps" -w /apps alpine/helm:3.14.4 "$@"
    return
  fi
  echo "FAIL: neither helm nor docker available" >&2
  exit 1
}

echo "==> helm lint ${REL_CHART}"
run_helm lint "${REL_CHART}"

echo "==> helm template (default values)"
run_helm template ispf-smoke "${REL_CHART}" >/tmp/ispf-helm-smoke.yaml
test -s /tmp/ispf-helm-smoke.yaml
grep -q 'kind: Deployment' /tmp/ispf-helm-smoke.yaml
grep -q 'ispf.io/bl: BL-186' /tmp/ispf-helm-smoke.yaml

echo "==> helm template (analytics + edge hints)"
run_helm template ispf-smoke "${REL_CHART}" \
  --set analytics.enabled=true \
  --set edge.enabled=true \
  --set edge.arm64=true \
  >/tmp/ispf-helm-smoke-analytics.yaml
grep -q 'kind: Deployment' /tmp/ispf-helm-smoke-analytics.yaml

echo "Helm chart validate OK (BL-186)"
