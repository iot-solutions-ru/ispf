#!/usr/bin/env python3
"""Sign ISPF bundle manifest license block (0003)."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding


def canonical_json(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def content_sha256(manifest: dict) -> str:
    body = dict(manifest)
    body.pop("license", None)
    digest = hashlib.sha256(canonical_json(body).encode("utf-8")).hexdigest()
    return digest


def main() -> None:
    parser = argparse.ArgumentParser(description="Sign commercial bundle license for ISPF")
    parser.add_argument("--bundle", required=True, help="Path to bundle.json (without license section)")
    parser.add_argument("--bundle-id", required=True, help="appId / bundleId")
    parser.add_argument("--installation-id", required=True, help="From GET /api/v1/platform/installation-id")
    parser.add_argument("--private-key", required=True, help="PEM private key path")
    parser.add_argument("--min-platform-version", default="0.7.0")
    parser.add_argument("--days-valid", type=int, default=365)
    parser.add_argument("--out", help="Write bundle with license to this path (default: overwrite --bundle)")
    args = parser.parse_args()

    bundle_path = Path(args.bundle)
    manifest = json.loads(bundle_path.read_text(encoding="utf-8"))
    if manifest.get("license"):
        raise SystemExit("Remove existing license section before signing")

    sha = content_sha256(manifest)
    expires_at = (datetime.now(timezone.utc) + timedelta(days=args.days_valid)).replace(microsecond=0).isoformat()
    claims = {
        "bundleId": args.bundle_id,
        "minPlatformVersion": args.min_platform_version,
        "installationId": args.installation_id,
        "contentSha256": sha,
        "expiresAt": expires_at,
    }
    payload = canonical_json(claims).encode("utf-8")

    private_key = serialization.load_pem_private_key(
        Path(args.private_key).read_bytes(),
        password=None,
    )
    signature = private_key.sign(payload, padding.PKCS1v15(), hashes.SHA256())
    license_block = dict(claims)
    license_block["signature"] = base64.b64encode(signature).decode("ascii")

    manifest["license"] = license_block
    out_path = Path(args.out) if args.out else bundle_path
    out_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote signed bundle to {out_path}")
    print(json.dumps(license_block, indent=2))


if __name__ == "__main__":
    main()
