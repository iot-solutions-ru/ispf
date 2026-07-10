#!/usr/bin/env bash
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
cd "$APP_DIR/server"

source "$APP_DIR/.env"
PSQL=(docker exec -i ispf-marketplace-postgres-1 psql -U marketplace -d marketplace -v ON_ERROR_STOP=1)

mkdir -p "$APP_DIR/data/artifacts"
cp "$APP_DIR/seed/ispf-analytics-energy-pack__1.0.0.zip" "$APP_DIR/data/artifacts/ispf-analytics-energy-pack__1.0.0.zip"
HASH=$(sha256sum "$APP_DIR/data/artifacts/ispf-analytics-energy-pack__1.0.0.zip" | awk '{print $1}')

"${PSQL[@]}" <<SQL
WITH vendor AS (
  SELECT id FROM vendors WHERE slug = 'iot-solutions' LIMIT 1
), listing AS (
  INSERT INTO listings (vendor_id, slug, title, description, kind, pricing, price_cents, min_ispf_version, app_id, pack_id, status, published_at)
  SELECT id,
    'ispf-analytics-energy-pack',
    'ISPF Energy Pack — energyDelta',
    'Free Tier C analytics extension: energyDelta(sourcePath, window) — energy consumption delta over a historian window.',
    'analytics_pack',
    'free',
    NULL,
    '0.9.127',
    NULL,
    'ispf-analytics-energy-pack',
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
    status = 'published',
    published_at = now()
  RETURNING id
)
INSERT INTO listing_versions (listing_id, version, artifact_path, artifact_sha256, changelog, moderation_status, reviewed_at, published_at)
SELECT listing.id, '1.0.0', 'ispf-analytics-energy-pack__1.0.0.zip', '${HASH}',
  'Initial energy analytics pack with energyDelta historian helper.', 'approved', now(), now()
FROM listing
ON CONFLICT (listing_id, version) DO UPDATE SET
  artifact_path = EXCLUDED.artifact_path,
  artifact_sha256 = EXCLUDED.artifact_sha256,
  changelog = EXCLUDED.changelog,
  moderation_status = 'approved',
  reviewed_at = now(),
  published_at = now();
SQL

npm run build
cd "$APP_DIR"
docker compose -f docker-compose.prod.yml --env-file "$APP_DIR/.env" up -d --build api

echo "=== Smoke ==="
curl -fsS "http://127.0.0.1:8090/api/v1/catalog/ispf-analytics-energy-pack"
echo ""
BYTES=$(curl -fsS "http://127.0.0.1:8090/api/v1/catalog/ispf-analytics-energy-pack/download" | wc -c)
echo "download bytes: $BYTES"
