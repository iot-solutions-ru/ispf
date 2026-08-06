#!/usr/bin/env bash
# ADR-0054: free /download for ui_pack ZIP + expose artifactKind/uiPackSlug in catalog.
# Safe to re-run. Ensures readBinaryArtifact helpers exist.
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
cd "$APP_DIR/server"

PSQL=(docker exec -i ispf-marketplace-postgres-1 psql -U marketplace -d marketplace -v ON_ERROR_STOP=1)
"${PSQL[@]}" <<'SQL'
ALTER TABLE listings ADD COLUMN IF NOT EXISTS pack_id TEXT;
ALTER TABLE listings ADD COLUMN IF NOT EXISTS ui_pack_slug TEXT;
ALTER TABLE listings ALTER COLUMN app_id DROP NOT NULL;
SQL

NEED_REBUILD=0

python3 <<'PY'
from pathlib import Path

# --- artifacts.ts: binary helpers ---
p = Path("src/storage/artifacts.ts")
text = p.read_text(encoding="utf-8")
if "readBinaryArtifact" in text:
    print("artifacts.ts already has readBinaryArtifact")
else:
    marker = "export async function deleteArtifact"
    if marker not in text:
        raise SystemExit("artifacts.ts: deleteArtifact marker not found")
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
    p.write_text(text.replace(marker, block + marker), encoding="utf-8")
    print("patched artifacts.ts")
    Path("/tmp/marketplace-ui-pack-need-rebuild").write_text("1", encoding="utf-8")
PY

python3 <<'PY'
from pathlib import Path

p = Path("src/routes/download.ts")
text = p.read_text(encoding="utf-8")
changed = False

if "readBinaryArtifact" not in text:
    text = text.replace(
        'import { readArtifact } from "../storage/artifacts.js";',
        'import { readArtifact, readBinaryArtifact } from "../storage/artifacts.js";',
    )
    changed = True

# Ensure resolveLatestVersion returns kind/packId (analytics patch may already have done this)
text = text.replace(
    "async function resolveLatestVersion(listingSlug: string): Promise<{\n  listingId: string;\n  appId: string;\n  pricing: string;",
    "async function resolveLatestVersion(listingSlug: string): Promise<{\n  listingId: string;\n  appId: string | null;\n  kind: string;\n  packId: string | null;\n  pricing: string;",
)
text = text.replace(
    "    listing_id: string;\n    app_id: string;\n    pricing: string;",
    "    listing_id: string;\n    app_id: string | null;\n    kind: string;\n    pack_id: string | null;\n    pricing: string;",
)
text = text.replace(
    "    `SELECT l.id AS listing_id, l.app_id, l.pricing, l.min_ispf_version,",
    "    `SELECT l.id AS listing_id, l.app_id, l.kind, l.pack_id, l.pricing, l.min_ispf_version,",
)
text = text.replace(
    "    appId: row.app_id,\n    pricing: row.pricing,",
    "    appId: row.app_id,\n    kind: row.kind,\n    packId: row.pack_id,\n    pricing: row.pricing,",
)

old_analytics = """      if (resolved.kind === "analytics_pack") {
        const bytes = await readBinaryArtifact(resolved.artifactPath, req.params.slug, resolved.version);
        reply.header("Content-Type", "application/zip");
        reply.header(
          "Content-Disposition",
          `attachment; filename="${resolved.packId ?? req.params.slug}-analytics-pack.zip"`
        );
        return reply.send(bytes);
      }"""

new_binary = """      if (resolved.kind === "analytics_pack" || resolved.kind === "ui_pack") {
        const bytes = await readBinaryArtifact(resolved.artifactPath, req.params.slug, resolved.version);
        const suffix = resolved.kind === "ui_pack" ? "ui-pack.zip" : "analytics-pack.zip";
        reply.header("Content-Type", "application/zip");
        reply.header(
          "Content-Disposition",
          `attachment; filename="${resolved.packId ?? req.params.slug}-${suffix}"`
        );
        return reply.send(bytes);
      }"""

if "resolved.kind === \"ui_pack\"" in text and "analytics_pack" in text:
    print("download.ts already has ui_pack ZIP path")
elif old_analytics in text:
    text = text.replace(old_analytics, new_binary)
    changed = True
    print("patched download.ts: widen ZIP path to ui_pack")
elif new_binary.strip() in text:
    print("download.ts already has combined ZIP path")
else:
    # Insert before JSON free-download block
    needle = """      const manifest = await readArtifact(resolved.artifactPath, req.params.slug, resolved.version);
      if (manifest.license) {
        return reply.code(500).send({ error: "Free artifact must not contain license block" });
      }"""
    if needle not in text:
        raise SystemExit("download.ts free-download block not found for ui_pack patch")
    text = text.replace(needle, new_binary + "\n\n" + needle)
    changed = True
    print("patched download.ts: inserted binary ZIP branch")

if changed:
    p.write_text(text, encoding="utf-8")
    Path("/tmp/marketplace-ui-pack-need-rebuild").write_text("1", encoding="utf-8")
PY

python3 <<'PY'
from pathlib import Path

p = Path("src/routes/catalog.ts")
text = p.read_text(encoding="utf-8")
changed = False

# artifactKind mapping
old_ak = 'artifactKind: row.kind === "analytics_pack" ? "analytics-pack" : row.kind,'
new_ak = '''artifactKind: row.kind === "analytics_pack"
      ? "analytics-pack"
      : row.kind === "ui_pack"
        ? "ui-pack"
        : row.kind,'''
if 'row.kind === "ui_pack"' in text and "analytics-pack" in text:
    print("catalog.ts artifactKind mapping already includes ui_pack")
elif old_ak in text:
    text = text.replace(old_ak, new_ak)
    changed = True
    print("patched catalog.ts artifactKind mapping")
elif "artifactKind:" not in text:
    # Older catalog without packId — apply minimal packId + artifactKind patch
    text = text.replace(
        "  appId: string;\n  vendorSlug:",
        "  appId: string | null;\n  packId: string | null;\n  uiPackSlug: string | null;\n  artifactKind: string;\n  vendorSlug:",
    )
    text = text.replace(
        "    appId: row.app_id,\n    vendorSlug:",
        "    appId: row.app_id,\n    packId: row.pack_id,\n    uiPackSlug: row.ui_pack_slug,\n    artifactKind: row.kind === \"analytics_pack\" ? \"analytics-pack\" : row.kind === \"ui_pack\" ? \"ui-pack\" : row.kind,\n    vendorSlug:",
    )
    text = text.replace(
        "            l.min_ispf_version, l.app_id,",
        "            l.min_ispf_version, l.app_id, l.pack_id, l.ui_pack_slug,",
    )
    text = text.replace(
        "    app_id: string;\n    vendor_slug:",
        "    app_id: string | null;\n    pack_id: string | null;\n    ui_pack_slug: string | null;\n    vendor_slug:",
    )
    changed = True
    print("patched catalog.ts from legacy shape")

# uiPackSlug on modern catalog
if "uiPackSlug" not in text:
    if "packId: string | null;" in text:
        text = text.replace(
            "packId: string | null;\n  artifactKind: string;",
            "packId: string | null;\n  uiPackSlug: string | null;\n  artifactKind: string;",
        )
        changed = True
    if "l.pack_id," in text and "l.ui_pack_slug" not in text:
        text = text.replace("l.pack_id,", "l.pack_id, l.ui_pack_slug,")
        changed = True
    if "pack_id: string | null;" in text and "ui_pack_slug: string | null;" not in text:
        text = text.replace(
            "pack_id: string | null;\n    vendor_slug:",
            "pack_id: string | null;\n    ui_pack_slug: string | null;\n    vendor_slug:",
        )
        changed = True
    if "packId: row.pack_id," in text and "uiPackSlug: row.ui_pack_slug" not in text:
        text = text.replace(
            "packId: row.pack_id,",
            "packId: row.pack_id,\n    uiPackSlug: row.ui_pack_slug,",
        )
        changed = True
    print("patched catalog.ts uiPackSlug")
else:
    print("catalog.ts already exposes uiPackSlug")

# Detail endpoint often reuses same SELECT — also patch common listing detail mapper if present
if "uiPackSlug: row.ui_pack_slug" in text and "ui_pack_slug" in text:
    pass

if changed:
    p.write_text(text, encoding="utf-8")
    Path("/tmp/marketplace-ui-pack-need-rebuild").write_text("1", encoding="utf-8")
PY

if [[ -f /tmp/marketplace-ui-pack-need-rebuild ]]; then
  rm -f /tmp/marketplace-ui-pack-need-rebuild
  npm run build
  cd "$APP_DIR"
  docker compose -f docker-compose.prod.yml --env-file "$APP_DIR/.env" up -d --build api
  echo "Marketplace ui-pack download/catalog patch applied + api rebuilt"
else
  echo "Marketplace ui-pack download/catalog patch already present"
fi
