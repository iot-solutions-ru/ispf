#!/usr/bin/env bash
# BL-183: seed all examples/marketplace-catalog listings on marketplace VPS.
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
SEED_DIR="${1:-$APP_DIR/seed/catalog}"
cd "$APP_DIR/server"

source "$APP_DIR/.env"
PSQL=(docker exec -i ispf-marketplace-postgres-1 psql -U marketplace -d marketplace -v ON_ERROR_STOP=1)
ARTIFACTS="$APP_DIR/data/artifacts"
mkdir -p "$ARTIFACTS" "$SEED_DIR"

if [[ -x /tmp/patch-marketplace-free-download-signing.sh ]]; then
  bash /tmp/patch-marketplace-free-download-signing.sh
fi

python3 <<PY
import hashlib
import json
import subprocess
from pathlib import Path

seed_dir = Path("$SEED_DIR")
artifacts = Path("$ARTIFACTS")
psql_base = ["docker", "exec", "-i", "ispf-marketplace-postgres-1", "psql", "-U", "marketplace", "-d", "marketplace", "-v", "ON_ERROR_STOP=1"]

def run_sql(sql: str) -> None:
    subprocess.run(psql_base, input=sql.encode("utf-8"), check=True)

count = 0
for listing_dir in sorted(seed_dir.iterdir()):
    if not listing_dir.is_dir():
        continue
    manifest_path = listing_dir / "listing.manifest.json"
    if not manifest_path.is_file():
        continue
    listing = json.loads(manifest_path.read_text(encoding="utf-8"))
    slug = listing.get("slug") or listing_dir.name
    version = listing.get("latestVersion") or "1.0.0"
    artifact_file = listing.get("bundleArtifact") or "bundle.json"
    bundle_path = listing_dir / artifact_file
    if not bundle_path.is_file():
        print(f"SKIP {slug}: missing {bundle_path.name}")
        continue

    stored = f"{slug}__{version}.json"
    target = artifacts / stored
    target.write_bytes(bundle_path.read_bytes())
    digest = hashlib.sha256(target.read_bytes()).hexdigest()

    title = listing.get("title", slug).replace("'", "''")
    description = listing.get("description", "").replace("'", "''")
    pricing = listing.get("pricing", "free")
    price_cents = listing.get("priceCents")
    price_sql = "NULL" if price_cents is None else str(int(price_cents))
    app_id = listing.get("appId")
    app_sql = "NULL" if not app_id else f"'{app_id}'"
    min_ispf = listing.get("minIspfVersion") or "0.9.30"
    changelog = listing.get("changelog", f"Catalog seed {version}").replace("'", "''")
    kind = "analytics_pack" if listing.get("artifactKind") == "analytics-pack" else "application"

    sql = f"""
WITH vendor AS (
  SELECT id FROM vendors WHERE slug = 'iot-solutions' LIMIT 1
), listing AS (
  INSERT INTO listings (vendor_id, slug, title, description, kind, pricing, price_cents, min_ispf_version, app_id, pack_id, status, published_at)
  SELECT id,
    '{slug}',
    '{title}',
    '{description}',
    '{kind}',
    '{pricing}',
    {price_sql},
    '{min_ispf}',
    {app_sql},
    NULL,
    'published',
    now()
  FROM vendor
  ON CONFLICT (slug) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    kind = EXCLUDED.kind,
    pricing = EXCLUDED.pricing,
    price_cents = EXCLUDED.price_cents,
    min_ispf_version = EXCLUDED.min_ispf_version,
    app_id = EXCLUDED.app_id,
    status = 'published',
    published_at = now()
  RETURNING id
)
INSERT INTO listing_versions (listing_id, version, artifact_path, artifact_sha256, changelog, moderation_status, reviewed_at, published_at)
SELECT listing.id, '{version}', '{stored}', '{digest}',
  '{changelog}', 'approved', now(), now()
FROM listing
ON CONFLICT (listing_id, version) DO UPDATE SET
  artifact_path = EXCLUDED.artifact_path,
  artifact_sha256 = EXCLUDED.artifact_sha256,
  changelog = EXCLUDED.changelog,
  moderation_status = 'approved',
  reviewed_at = now(),
  published_at = now();
"""
    run_sql(sql)
    count += 1
    print(f"seeded {slug} -> {stored}")

print(f"Done: {count} listings seeded from {seed_dir}")
PY

echo "=== Catalog smoke ==="
curl -fsS "http://127.0.0.1:8090/api/v1/catalog" | python3 -c "import sys,json; d=json.load(sys.stdin); print('listings:', len(d.get('listings',[])))"
INSTALL_ID="$(curl -fsS 'https://ispf.example.invalid/api/v1/platform/installation-id' -H 'X-ISPF-Role: admin' 2>/dev/null | python3 -c 'import sys,json; print(json.load(sys.stdin).get(\"installationId\",\"\"))' 2>/dev/null || echo '')"
if [[ -n "$INSTALL_ID" ]]; then
  BYTES=$(curl -fsS "http://127.0.0.1:8090/api/v1/catalog/mes-platform/download?installationId=${INSTALL_ID}" | python3 -c "import sys,json; d=json.load(sys.stdin); print('license' in d)")
  echo "mes-platform signed download has license: $BYTES"
else
  echo "skip signed download smoke (could not read prod installation-id)"
fi
