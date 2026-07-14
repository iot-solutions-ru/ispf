#!/usr/bin/env bash
# Fix free /download for analytics_pack ZIP artifacts + expose packId in catalog.
# Safe to re-run. Does not re-seed listings.
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
cd "$APP_DIR/server"

python3 <<'PY'
from pathlib import Path

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
PY

python3 <<'PY'
from pathlib import Path

p = Path("src/routes/download.ts")
text = p.read_text(encoding="utf-8")
if "resolved.kind === \"analytics_pack\"" in text and "readBinaryArtifact" in text:
    print("download.ts free ZIP path already present")
else:
    if "readBinaryArtifact" not in text:
        text = text.replace(
            'import { readArtifact } from "../storage/artifacts.js";',
            'import { readArtifact, readBinaryArtifact } from "../storage/artifacts.js";',
        )
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
    if old not in text:
        raise SystemExit("download.ts free-download block not found for patch")
    text = text.replace(old, new)
    p.write_text(text, encoding="utf-8")
    print("patched download.ts free ZIP path")
PY

python3 <<'PY'
from pathlib import Path

p = Path("src/routes/catalog.ts")
text = p.read_text(encoding="utf-8")
if "packId:" in text and "l.pack_id" in text:
    print("catalog.ts already exposes packId")
else:
    text = text.replace(
        "  appId: string;\n  vendorSlug:",
        "  appId: string | null;\n  packId: string | null;\n  artifactKind: string;\n  vendorSlug:",
    )
    text = text.replace(
        "    appId: row.app_id,\n    vendorSlug:",
        "    appId: row.app_id,\n    packId: row.pack_id,\n    artifactKind: row.kind === \"analytics_pack\" ? \"analytics-pack\" : row.kind,\n    vendorSlug:",
    )
    text = text.replace(
        "            l.min_ispf_version, l.app_id,",
        "            l.min_ispf_version, l.app_id, l.pack_id,",
    )
    text = text.replace(
        "    app_id: string;\n    vendor_slug:",
        "    app_id: string | null;\n    pack_id: string | null;\n    vendor_slug:",
    )
    # also expose packId in ISPF referenceExamples if present
    if "referenceExamples:" in text and "packId: item.packId" not in text:
        text = text.replace(
            "      appId: item.appId,\n      title: item.title,",
            "      appId: item.appId,\n      packId: item.packId,\n      kind: item.kind,\n      artifactKind: item.artifactKind,\n      title: item.title,",
        )
    p.write_text(text, encoding="utf-8")
    print("patched catalog.ts packId")
PY

npm run build
cd "$APP_DIR"
docker compose -f docker-compose.prod.yml --env-file "$APP_DIR/.env" up -d --build api

echo "=== Smoke catalog detail ==="
curl -fsS "http://127.0.0.1:8090/api/v1/catalog/ispf-analytics-kpi-demo"
echo ""
echo "=== Smoke download (expect PK zip magic) ==="
HDR=$(curl -fsS -D - -o /tmp/analytics-kpi.zip "http://127.0.0.1:8090/api/v1/catalog/ispf-analytics-kpi-demo/download" | tr -d '\r')
echo "$HDR" | head -20
python3 - <<'PY'
from pathlib import Path
b = Path("/tmp/analytics-kpi.zip").read_bytes()
print("bytes", len(b), "magic", b[:2])
assert b[:2] == b"PK", b[:16]
print("OK zip download")
PY

curl -fsS -D - -o /tmp/analytics-energy.zip "http://127.0.0.1:8090/api/v1/catalog/ispf-analytics-energy-pack/download" >/dev/null
python3 - <<'PY'
from pathlib import Path
b = Path("/tmp/analytics-energy.zip").read_bytes()
print("energy bytes", len(b), "magic", b[:2])
assert b[:2] == b"PK"
print("OK energy zip download")
PY
