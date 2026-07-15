#!/usr/bin/env bash
# BL-183: patch marketplace free download to RSA-sign bundles when installationId is provided.
# No-op if download.ts already has signing support.
set -euo pipefail
APP_DIR=/opt/ispf-marketplace
cd "$APP_DIR/server"
rm -f /tmp/marketplace-download-patched

python3 <<'PY'
from pathlib import Path

path = Path("src/routes/download.ts")
text = path.read_text(encoding="utf-8")

if "installationId?: string" in text and "signBundleManifest" in text:
    print("download.ts already supports free download signing — skip patch")
    raise SystemExit(0)

old_sig = """      req: FastifyRequest<{ Params: { slug: string }; Querystring: { version?: string } }>,"""
new_sig = """      req: FastifyRequest<{ Params: { slug: string }; Querystring: { version?: string; installationId?: string } }>,"""
if old_sig not in text:
    print("download.ts signature block not found — skip patch (already newer)")
    raise SystemExit(0)
text = text.replace(old_sig, new_sig)

old_block = """      reply.header("Content-Type", "application/json; charset=utf-8");
      reply.header("Content-Disposition", `attachment; filename="${resolved.appId ?? req.params.slug}-bundle.json"`);
      reply.send(manifest);"""

new_block = """      const installationId = req.query.installationId?.trim();
      if (installationId) {
        if (!config.signingPrivateKeyPem) {
          return reply.code(503).send({ error: "Marketplace signing key is not configured" });
        }
        const signed = signBundleManifest(manifest, {
          bundleId: resolved.appId ?? req.params.slug,
          installationId,
          privateKeyPem: config.signingPrivateKeyPem,
          minPlatformVersion: resolved.minIspfVersion,
        });
        reply.header("Content-Type", "application/json; charset=utf-8");
        reply.header(
          "Content-Disposition",
          `attachment; filename="${resolved.appId ?? req.params.slug}-bundle.json"`
        );
        return reply.send(signed);
      }

      reply.header("Content-Type", "application/json; charset=utf-8");
      reply.header("Content-Disposition", `attachment; filename="${resolved.appId ?? req.params.slug}-bundle.json"`);
      reply.send(manifest);"""

if old_block not in text:
    print("download.ts send block not found — skip patch (already newer)")
    raise SystemExit(0)
text = text.replace(old_block, new_block)
path.write_text(text, encoding="utf-8")
Path("/tmp/marketplace-download-patched").write_text("1", encoding="utf-8")
print("patched download.ts for installationId signing")
PY

if [[ -f /tmp/marketplace-download-patched ]]; then
  rm -f /tmp/marketplace-download-patched
  npm run build
  cd "$APP_DIR"
  docker compose -f docker-compose.prod.yml --env-file "$APP_DIR/.env" up -d --build api
  echo "Marketplace download signing patch applied"
else
  echo "Marketplace download signing patch skipped"
fi
