#!/usr/bin/env bash
# BL-216: wire signAnalyticsPackZip into /entitlements/activate in download.ts.
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
cd "$APP_DIR/server"

python3 <<'PY'
from pathlib import Path
p = Path("src/storage/artifacts.ts")
text = p.read_text(encoding="utf-8")
if "readBinaryArtifact" not in text:
    marker = "export async function deleteArtifact"
    block = '''
export function binaryArtifactPath(listingSlug: string, version: string): string {
  const safe = `${listingSlug}__${version}`.replace(/[^a-zA-Z0-9._-]/g, "_");
  return path.join(config.artifactsDir, `${safe}.zip`);
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
else:
    print("artifacts.ts already has readBinaryArtifact")
PY

python3 <<'PY'
from pathlib import Path

path = Path("src/routes/download.ts")
text = path.read_text(encoding="utf-8")

if "signAnalyticsPackZip" in text and "row.kind === \"analytics_pack\"" in text:
    print("download.ts already signs analytics packs on activate")
    raise SystemExit(0)

if 'import { signBundleManifest }' in text and "signAnalyticsPackZip" not in text:
    text = text.replace(
        'import { signBundleManifest } from "../license/signBundle.js";',
        'import { signBundleManifest } from "../license/signBundle.js";\nimport { signAnalyticsPackZip } from "../license/signAnalyticsPackZip.js";',
    )

if "readBinaryArtifact" not in text:
    text = text.replace(
        'import { readArtifact } from "../storage/artifacts.js";',
        'import { readArtifact, readBinaryArtifact } from "../storage/artifacts.js";',
    )

replacements = [
    (
        "l.slug, l.app_id, l.pricing, l.min_ispf_version",
        "l.slug, l.app_id, l.kind, l.pack_id, l.pricing, l.min_ispf_version",
    ),
    (
        "slug: string;\n        app_id: string;\n        pricing: string;",
        "slug: string;\n        app_id: string | null;\n        kind: string;\n        pack_id: string | null;\n        pricing: string;",
    ),
    (
        "row.slug, row.app_id, row.pricing, row.min_ispf_version",
        "row.slug, row.app_id, row.kind, row.pack_id, row.pricing, row.min_ispf_version",
    ),
]

for old, new in replacements:
    if old in text:
        text = text.replace(old, new)

needle = "      const manifest = await readArtifact(resolved.artifactPath, row.slug, resolved.version);\n      const signed = signBundleManifest(manifest, {"
if needle not in text:
  # partial patch from failed run — try alternate needle
    needle = "      if (row.kind === \"analytics_pack\") {"
    if needle in text:
        print("download.ts analytics_pack block already present (fix types manually if build fails)")
        path.write_text(text, encoding="utf-8")
        raise SystemExit(0)
    raise SystemExit("activate signBundle block not found in download.ts")

block = """      if (row.kind === "analytics_pack") {
        const rawZip = await readBinaryArtifact(resolved.artifactPath, row.slug, resolved.version);
        const signedZip = signAnalyticsPackZip({
          zipBytes: rawZip,
          packId: row.pack_id ?? row.slug,
          installationId,
          privateKeyPem: config.signingPrivateKeyPem,
          minPlatformVersion: row.min_ispf_version,
        });
        if (existing.rowCount === 0) {
          await query(
            `INSERT INTO activations (entitlement_id, installation_id) VALUES ($1, $2)`,
            [row.id, installationId]
          );
          await query(`UPDATE entitlements SET activation_count = activation_count + 1 WHERE id = $1`, [row.id]);
        }
        return reply.send({
          slug: row.slug,
          packId: row.pack_id,
          artifactKind: "analytics-pack",
          artifactBytesBase64: signedZip.toString("base64"),
        });
      }

      const manifest = await readArtifact(resolved.artifactPath, row.slug, resolved.version);
      const signed = signBundleManifest(manifest, {"""

text = text.replace(needle, block, 1)
text = text.replace(
    "bundleId: row.app_id,",
    "bundleId: row.app_id ?? row.slug,",
)
path.write_text(text, encoding="utf-8")
print("patched download.ts activate for analytics_pack signing")
PY

if ! grep -q '"fflate"' package.json 2>/dev/null; then
  npm install fflate
fi

npm run build
cd "$APP_DIR"
docker compose -f docker-compose.prod.yml --env-file "$APP_DIR/.env" up -d --build api
echo "Marketplace analytics-pack activate signing wired"
