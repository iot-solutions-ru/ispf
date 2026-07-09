#!/usr/bin/env bash
# Sync examples/marketplace-catalog to ispf-marketplace VPS seed/ and reseed DB + artifacts.
# Run from dev machine with SSH to marketplace host:
#   bash deploy/tools/marketplace-vps-sync-catalog.sh root@marketplace.ispf.ai
set -euo pipefail

REMOTE_HOST="${1:-root@marketplace.ispf.ai}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CATALOG="$ROOT/examples/marketplace-catalog"
APP_DIR="/opt/ispf-marketplace"
SEED_DIR="$APP_DIR/seed"
GEN_PY="$ROOT/deploy/tools/marketplace-generate-seed-listings.py"
SEED_TS="$APP_DIR/server/scripts/seed-demo.ts"

log() { echo "==> $*"; }

log "Upload catalog bundles to ${REMOTE_HOST}:${SEED_DIR}"
ssh -o BatchMode=yes "$REMOTE_HOST" "mkdir -p '$SEED_DIR'"

python3 "$GEN_PY" > /tmp/marketplace-listings.ts.txt

for dir in "$CATALOG"/*/; do
  [ -f "${dir}listing.manifest.json" ] || continue
  [ -f "${dir}bundle.json" ] || continue
  slug="$(python3 -c 'import json; print(json.load(open("'"${dir}listing.manifest.json"'"))["slug"])')"
  case "$slug" in
    building-hvac) slug="building-hvac-reference" ;;
    mes-reference) slug="mes-reference-paid-demo" ;;
  esac
  scp -o BatchMode=yes "${dir}bundle.json" "${REMOTE_HOST}:${SEED_DIR}/${slug}-bundle.json"
done

if [ -f "$ROOT/examples/mes-platform-production/bundle.json" ]; then
  scp -o BatchMode=yes "$ROOT/examples/mes-platform-production/bundle.json" \
    "${REMOTE_HOST}:${SEED_DIR}/mes-platform-production-bundle.json"
fi

log "Patch LISTINGS in seed-demo.ts on marketplace VPS"
scp -o BatchMode=yes /tmp/marketplace-listings.ts.txt "${REMOTE_HOST}:/tmp/marketplace-listings.ts.txt"

ssh -o BatchMode=yes "$REMOTE_HOST" bash <<'REMOTE'
set -euo pipefail
SEED_TS="/opt/ispf-marketplace/server/scripts/seed-demo.ts"
LISTINGS_FILE="/tmp/marketplace-listings.ts.txt"
python3 <<'PY'
import re
from pathlib import Path

seed_ts = Path("/opt/ispf-marketplace/server/scripts/seed-demo.ts")
listings = Path("/tmp/marketplace-listings.ts.txt").read_text(encoding="utf-8").strip()
text = seed_ts.read_text(encoding="utf-8")
pat = r"const LISTINGS: ListingSeed\[\] = \[.*?\];"
if not re.search(pat, text, flags=re.S):
    raise SystemExit("LISTINGS block not found in seed-demo.ts")
new_text = re.sub(pat, listings, text, count=1, flags=re.S)
seed_ts.write_text(new_text, encoding="utf-8")
print("Patched seed-demo.ts LISTINGS")
PY
bash /opt/ispf-marketplace/deploy/vps-reseed-artifacts.sh
REMOTE

log "Verify catalog count"
curl -fsS "https://marketplace.ispf.ai/api/v1/catalog" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print("listings:", len(d.get("listings",[]))); [print(" -", x["slug"]) for x in d.get("listings",[])]'
