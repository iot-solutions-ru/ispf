#!/usr/bin/env bash
# BL-183 + ADR-0054: seed examples/marketplace-catalog on marketplace VPS.
# Supports application JSON bundles, analytics-pack ZIP, and ui-pack ZIP (+ uiPackSlug).
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
SEED_DIR="${1:-$APP_DIR/seed/catalog}"
cd "$APP_DIR/server"

source "$APP_DIR/.env"
PSQL=(docker exec -i ispf-marketplace-postgres-1 psql -U marketplace -d marketplace -v ON_ERROR_STOP=1)
ARTIFACTS="$APP_DIR/data/artifacts"
mkdir -p "$ARTIFACTS" "$SEED_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCH_SCRIPT="$SCRIPT_DIR/patch-marketplace-free-download-signing.sh"
if [[ -x "$PATCH_SCRIPT" ]]; then
  bash "$PATCH_SCRIPT"
fi

UI_PACK_PATCH="$SCRIPT_DIR/patch-marketplace-ui-pack-download-remote.sh"
if [[ -x "$UI_PACK_PATCH" ]]; then
  bash "$UI_PACK_PATCH"
fi

"${PSQL[@]}" <<'SQL'
ALTER TABLE listings ADD COLUMN IF NOT EXISTS pack_id TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS ui_pack_slug TEXT;
ALTER TABLE listings ALTER COLUMN app_id DROP NOT NULL;
SQL

export SEED_DIR ARTIFACTS
python3 <<'PY'
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

seed_dir = Path(os.environ["SEED_DIR"])
artifacts = Path(os.environ["ARTIFACTS"])
psql_base = [
    "docker", "exec", "-i", "ispf-marketplace-postgres-1",
    "psql", "-U", "marketplace", "-d", "marketplace", "-v", "ON_ERROR_STOP=1",
]

SAFE_SLUG = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
SAFE_VERSION = re.compile(r"[A-Za-z0-9][A-Za-z0-9.+_-]{0,63}")


def run_sql(sql: str) -> None:
    subprocess.run(psql_base, input=sql.encode("utf-8"), check=True)


def sql_str(value: str) -> str:
    return "'" + str(value).replace("'", "''") + "'"


def sql_nullable(value) -> str:
    if value is None or value == "":
        return "NULL"
    return sql_str(value)


def normalize_artifact_kind(listing: dict) -> str:
    raw = str(listing.get("artifactKind") or listing.get("kind") or "").strip().lower().replace("_", "-")
    if raw in ("analytics-pack", "ui-pack", "symbol-pack", "workflow-template"):
        return raw
    return "application"


def db_kind(artifact_kind: str) -> str:
    return {
        "analytics-pack": "analytics_pack",
        "ui-pack": "ui_pack",
        "symbol-pack": "symbol_pack",
        "workflow-template": "workflow_template",
        "application": "application",
    }.get(artifact_kind, "application")


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
    if not SAFE_SLUG.fullmatch(slug) or not SAFE_VERSION.fullmatch(version):
        print(f"SKIP {listing_dir.name}: unsafe slug/version {slug!r}/{version!r}")
        continue

    artifact_kind = normalize_artifact_kind(listing)
    kind = db_kind(artifact_kind)
    artifact_file = listing.get("bundleArtifact") or (
        "bundle.json" if artifact_kind == "application" else None
    )
    if not artifact_file:
        print(f"SKIP {slug}: missing bundleArtifact for {artifact_kind}")
        continue
    bundle_path = listing_dir / artifact_file
    if not bundle_path.is_file():
        print(f"SKIP {slug}: missing {bundle_path.name}")
        continue

    is_zip = artifact_kind in ("analytics-pack", "ui-pack", "symbol-pack") or bundle_path.suffix.lower() == ".zip"
    stored = f"{slug}__{version}.{'zip' if is_zip else 'json'}"
    target = artifacts / stored
    target.write_bytes(bundle_path.read_bytes())
    digest = hashlib.sha256(target.read_bytes()).hexdigest()

    title = listing.get("title", slug)
    description = listing.get("description", "")
    pricing = listing.get("pricing", "free")
    price_cents = listing.get("priceCents")
    price_sql = "NULL" if price_cents is None else str(int(price_cents))
    app_id = listing.get("appId")
    pack_id = listing.get("packId") or (app_id if artifact_kind in ("ui-pack", "analytics-pack") else None)
    ui_pack_slug = listing.get("uiPackSlug")
    min_ispf = listing.get("minIspfVersion") or "0.9.30"
    changelog = listing.get("changelog", f"Catalog seed {version}")

    sql = f"""
WITH vendor AS (
  SELECT id FROM vendors WHERE slug = 'iot-solutions' LIMIT 1
), listing AS (
  INSERT INTO listings (
    vendor_id, slug, title, description, kind, pricing, price_cents,
    min_ispf_version, app_id, pack_id, ui_pack_slug, status, published_at
  )
  SELECT id,
    {sql_str(slug)},
    {sql_str(title)},
    {sql_str(description)},
    {sql_str(kind)},
    {sql_str(pricing)},
    {price_sql},
    {sql_str(min_ispf)},
    {sql_nullable(app_id)},
    {sql_nullable(pack_id)},
    {sql_nullable(ui_pack_slug)},
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
    pack_id = EXCLUDED.pack_id,
    ui_pack_slug = EXCLUDED.ui_pack_slug,
    status = 'published',
    published_at = now()
  RETURNING id
)
INSERT INTO listing_versions (listing_id, version, artifact_path, artifact_sha256, changelog, moderation_status, reviewed_at, published_at)
SELECT listing.id, {sql_str(version)}, {sql_str(stored)}, {sql_str(digest)},
  {sql_str(changelog)}, 'approved', now(), now()
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
    print(f"seeded {slug} kind={kind} -> {stored}")

print(f"Done: {count} listings seeded from {seed_dir}")
PY

echo "=== Catalog smoke ==="
curl -fsS "http://127.0.0.1:8090/api/v1/catalog" | python3 -c "import sys,json; d=json.load(sys.stdin); print('listings:', len(d.get('listings',[])))"
python3 <<'PY'
import json
import urllib.request

with urllib.request.urlopen("http://127.0.0.1:8090/api/v1/catalog") as resp:
    data = json.load(resp)
listings = {row.get("slug"): row for row in data.get("listings", [])}
for slug in ("oil-control", "oil-control-ui"):
    row = listings.get(slug)
    if not row:
        print(f"MISSING {slug}")
        continue
    print(
        f"{slug}: kind={row.get('kind')} artifactKind={row.get('artifactKind')} "
        f"packId={row.get('packId')} uiPackSlug={row.get('uiPackSlug')} ver={row.get('latestVersion')}"
    )
PY

if curl -fsS -o /tmp/oil-control-ui.zip -w "%{http_code}" \
  "http://127.0.0.1:8090/api/v1/catalog/oil-control-ui/download" | grep -q 200; then
  python3 - <<'PY'
from pathlib import Path
b = Path("/tmp/oil-control-ui.zip").read_bytes()
print("oil-control-ui download bytes", len(b), "magic", b[:2])
assert b[:2] == b"PK", b[:16]
print("OK ui-pack zip download")
PY
else
  echo "WARN: oil-control-ui download smoke failed (listing may be absent until seed completes)"
fi
