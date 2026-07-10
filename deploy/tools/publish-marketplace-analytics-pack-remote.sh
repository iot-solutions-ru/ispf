#!/usr/bin/env bash
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
cd "$APP_DIR/server"

source "$APP_DIR/.env"
PSQL=(docker exec -i ispf-marketplace-postgres-1 psql -U marketplace -d marketplace -v ON_ERROR_STOP=1)
"${PSQL[@]}" <<'SQL'
ALTER TABLE listings ADD COLUMN IF NOT EXISTS pack_id TEXT;
ALTER TABLE listings ALTER COLUMN app_id DROP NOT NULL;
SQL

cat > /tmp/patch-artifacts.py <<'PY'
from pathlib import Path
p = Path("src/storage/artifacts.ts")
text = p.read_text(encoding="utf-8")
if "writeBinaryArtifact" in text:
    print("artifacts.ts already patched")
else:
    marker = "export async function deleteArtifact"
    block = '''
export function binaryArtifactPath(listingSlug: string, version: string): string {
  const safe = `${listingSlug}__${version}`.replace(/[^a-zA-Z0-9._-]/g, "_");
  return path.join(config.artifactsDir, `${safe}.zip`);
}

export async function writeBinaryArtifact(
  listingSlug: string,
  version: string,
  bytes: Buffer
): Promise<{ path: string; sha256: string }> {
  await ensureArtifactsDir();
  const filePath = binaryArtifactPath(listingSlug, version);
  await fs.writeFile(filePath, bytes);
  const sha256 = crypto.createHash("sha256").update(bytes).digest("hex");
  return { path: path.basename(filePath), sha256 };
}

export async function readBinaryArtifact(
  storedPath: string,
  listingSlug?: string,
  version?: string
): Promise<Buffer> {
  const basename = path.basename(storedPath);
  const candidates = [
    path.join(config.artifactsDir, basename),
    ...(listingSlug && version ? [binaryArtifactPath(listingSlug, version)] : []),
  ];
  const seen = new Set<string>();
  for (const candidate of candidates) {
    const resolved = path.resolve(candidate);
    if (seen.has(resolved)) continue;
    seen.add(resolved);
    try {
      return await fs.readFile(resolved);
    } catch (err) {
      const code = (err as NodeJS.ErrnoException).code;
      if (code !== "ENOENT") throw err;
    }
  }
  const err = new Error(`Binary artifact not found for ${storedPath}`) as NodeJS.ErrnoException;
  err.code = "ENOENT";
  throw err;
}

'''
    text = text.replace(marker, block + marker)
    p.write_text(text, encoding="utf-8")
    print("patched artifacts.ts")
PY
python3 /tmp/patch-artifacts.py

python3 <<'PY'
from pathlib import Path
p = Path("src/routes/download.ts")
text = p.read_text(encoding="utf-8")
if "readBinaryArtifact" not in text:
    text = text.replace(
        'import { readArtifact } from "../storage/artifacts.js";',
        'import { readArtifact, readBinaryArtifact } from "../storage/artifacts.js";'
    )
    text = text.replace(
        "async function resolveLatestVersion(listingSlug: string): Promise<{\n  listingId: string;\n  appId: string;\n  pricing: string;",
        "async function resolveLatestVersion(listingSlug: string): Promise<{\n  listingId: string;\n  appId: string | null;\n  kind: string;\n  packId: string | null;\n  pricing: string;"
    )
    text = text.replace(
        "    listing_id: string;\n    app_id: string;\n    pricing: string;",
        "    listing_id: string;\n    app_id: string | null;\n    kind: string;\n    pack_id: string | null;\n    pricing: string;"
    )
    text = text.replace(
        "    `SELECT l.id AS listing_id, l.app_id, l.pricing, l.min_ispf_version,",
        "    `SELECT l.id AS listing_id, l.app_id, l.kind, l.pack_id, l.pricing, l.min_ispf_version,"
    )
    text = text.replace(
        "    appId: row.app_id,\n    pricing: row.pricing,",
        "    appId: row.app_id,\n    kind: row.kind,\n    packId: row.pack_id,\n    pricing: row.pricing,"
    )
    old = """      const manifest = await readArtifact(resolved.artifactPath, req.params.slug, resolved.version);
      if (manifest.license) {
        return reply.code(500).send({ error: "Free artifact must not contain license block" });
      }

      reply.header("Content-Type", "application/json; charset=utf-8");
      reply.header("Content-Disposition", `attachment; filename="${resolved.appId}-bundle.json"`);
      reply.send(manifest);"""
    new = """      if (resolved.kind === "analytics_pack") {
        const bytes = await readBinaryArtifact(resolved.artifactPath, req.params.slug, resolved.version);
        reply.header("Content-Type", "application/zip");
        reply.header(
          "Content-Disposition",
          `attachment; filename="${resolved.packId ?? req.params.slug}-analytics-pack.zip"`
        );
        return reply.send(bytes);
      }

      const manifest = await readArtifact(resolved.artifactPath, req.params.slug, resolved.version);
      if (manifest.license) {
        return reply.code(500).send({ error: "Free artifact must not contain license block" });
      }

      reply.header("Content-Type", "application/json; charset=utf-8");
      reply.header("Content-Disposition", `attachment; filename="${resolved.appId ?? req.params.slug}-bundle.json"`);
      reply.send(manifest);"""
    text = text.replace(old, new)
    p.write_text(text, encoding="utf-8")
    print("patched download.ts")
else:
    print("download.ts already patched")
PY

python3 <<'PY'
from pathlib import Path
p = Path("src/routes/catalog.ts")
text = p.read_text(encoding="utf-8")
if "packId" not in text:
    text = text.replace(
        "  appId: string;\n  vendorSlug:",
        "  appId: string | null;\n  packId: string | null;\n  artifactKind: string;\n  vendorSlug:"
    )
    text = text.replace(
        "    appId: row.app_id,\n    vendorSlug:",
        "    appId: row.app_id,\n    packId: row.pack_id,\n    artifactKind: row.kind === \"analytics_pack\" ? \"analytics-pack\" : row.kind,\n    vendorSlug:"
    )
    text = text.replace(
        "            l.min_ispf_version, l.app_id,",
        "            l.min_ispf_version, l.app_id, l.pack_id,"
    )
    text = text.replace(
        "    app_id: string;\n    vendor_slug:",
        "    app_id: string | null;\n    pack_id: string | null;\n    vendor_slug:"
    )
    p.write_text(text, encoding="utf-8")
    print("patched catalog.ts")
else:
    print("catalog.ts already patched")
PY

mkdir -p "$APP_DIR/data/artifacts"
cp "$APP_DIR/seed/ispf-analytics-kpi-demo__1.0.0.zip" "$APP_DIR/data/artifacts/ispf-analytics-kpi-demo__1.0.0.zip"
HASH=$(sha256sum "$APP_DIR/data/artifacts/ispf-analytics-kpi-demo__1.0.0.zip" | awk '{print $1}')

"${PSQL[@]}" <<SQL
WITH vendor AS (
  SELECT id FROM vendors WHERE slug = 'iot-solutions' LIMIT 1
), listing AS (
  INSERT INTO listings (vendor_id, slug, title, description, kind, pricing, price_cents, min_ispf_version, app_id, pack_id, status, published_at)
  SELECT id,
    'ispf-analytics-kpi-demo',
    'ISPF KPI Demo Pack — percentChange',
    'Free Tier C analytics extension: percentChange(sourcePath, window) — percent difference between first and last bucket average.',
    'analytics_pack',
    'free',
    NULL,
    '0.9.127',
    NULL,
    'ispf-analytics-kpi-demo',
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
SELECT listing.id, '1.0.0', 'ispf-analytics-kpi-demo__1.0.0.zip', '${HASH}',
  'Initial KPI demo pack with percentChange historian helper.', 'approved', now(), now()
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
curl -fsS "http://127.0.0.1:8090/api/v1/catalog/ispf-analytics-kpi-demo"
echo ""
BYTES=$(curl -fsS "http://127.0.0.1:8090/api/v1/catalog/ispf-analytics-kpi-demo/download" | wc -c)
echo "download bytes: $BYTES"
