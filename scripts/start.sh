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

echo "Starting ISPF (local profile)"
echo "  UI:       http://localhost:8080"
echo "  Login:    admin / admin"
echo "  Database: embedded H2 — ${SCRIPT_DIR}/data/ispf-local.mv.db"
echo "            (no separate PostgreSQL required)"
echo
exec java -jar "$JAR" --spring.profiles.active=local
