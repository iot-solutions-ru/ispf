#!/usr/bin/env bash
# ISPF all-in-one launcher (Linux / macOS). Requires JDK 25+ on PATH.
# Place this file next to ispf-server.jar, or pass the JAR path as $1.
# Database: embedded H2 file (no PostgreSQL). File: data/ispf-local.mv.db
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ $# -ge 1 ]]; then
  JAR="$1"
elif [[ -f ispf-server.jar ]]; then
  JAR="ispf-server.jar"
elif [[ -f ../ispf-server.jar ]]; then
  JAR="../ispf-server.jar"
else
  echo "Missing ispf-server.jar" >&2
  echo "Usage: $0 [path/to/ispf-server.jar]" >&2
  echo "Put start.sh next to the JAR, or pass the path." >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java not found on PATH. Install JDK 25+ and try again." >&2
  exit 1
fi

# Relative ./data keeps the H2 JDBC URL portable.
export ISPF_DATA_DIR="${ISPF_DATA_DIR:-./data}"
mkdir -p "$ISPF_DATA_DIR"

# Portable zip ships the evaluation set in ./drivers (not under data/, so a DB reset keeps it).
# If the launcher sits in scripts/ and the JAR is next to drivers/, use that folder instead.
if [[ -z "${ISPF_DRIVER_PACKS_DIR:-}" ]]; then
  if [[ -d "$SCRIPT_DIR/drivers" ]]; then
    export ISPF_DRIVER_PACKS_DIR="$SCRIPT_DIR/drivers"
  else
    JAR_DIR="$(cd "$(dirname "$JAR")" && pwd)"
    if [[ -d "$JAR_DIR/drivers" ]]; then
      export ISPF_DRIVER_PACKS_DIR="$JAR_DIR/drivers"
    fi
  fi
fi

echo "Starting ISPF (local profile)"
echo "  UI:       http://localhost:8080"
echo "  Login:    admin / admin"
echo "  Database: embedded H2 — ${SCRIPT_DIR}/data/ispf-local.mv.db"
echo "            (no separate PostgreSQL required)"
if [[ -n "${ISPF_DRIVER_PACKS_DIR:-}" ]]; then
  echo "  Drivers:  ${ISPF_DRIVER_PACKS_DIR}"
fi
echo
exec java -jar "$JAR" --spring.profiles.active=local
