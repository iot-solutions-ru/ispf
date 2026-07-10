#!/usr/bin/env python3
"""Sign analytics-pack.json license block (BL-216, same payload as driver packs)."""

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


def jar_sha256(jar_path: Path) -> str:
    digest = hashlib.sha256(jar_path.read_bytes()).hexdigest()
    return digest


def main() -> None:
    parser = argparse.ArgumentParser(description="Sign commercial analytics-pack license for ISPF")
    parser.add_argument("--manifest", required=True, help="Path to analytics-pack.json (without license section)")
    parser.add_argument("--jar", required=True, help="Path to pack JAR referenced by jarFile")
    parser.add_argument("--pack-id", required=True, help="packId in manifest")
    parser.add_argument("--installation-id", required=True, help="From GET /api/v1/platform/installation-id")
    parser.add_argument("--private-key", required=True, help="PEM private key path")
    parser.add_argument("--min-platform-version", default="0.9.127")
    parser.add_argument("--days-valid", type=int, default=365)
    parser.add_argument("--out", help="Write signed manifest to this path (default: overwrite --manifest)")
    args = parser.parse_args()

    manifest_path = Path(args.manifest)
    jar_path = Path(args.jar)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("license"):
        raise SystemExit("Remove existing license section before signing")

    sha = jar_sha256(jar_path)
    expires_at = (datetime.now(timezone.utc) + timedelta(days=args.days_valid)).replace(microsecond=0).isoformat()
    claims = {
        "packId": args.pack_id,
        "minPlatformVersion": args.min_platform_version,
        "installationId": args.installation_id,
        "jarSha256": sha,
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
    out_path = Path(args.out) if args.out else manifest_path
    out_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote signed analytics-pack manifest to {out_path}")
    print(json.dumps(license_block, indent=2))


if __name__ == "__main__":
    main()
