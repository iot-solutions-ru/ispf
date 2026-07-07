#!/usr/bin/env python3
"""Generate RSA key pair for ISPF commercial bundle licensing."""

from __future__ import annotations

import argparse
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate RSA keys for ISPF license signing")
    parser.add_argument("--out-dir", default=".", help="Output directory for PEM files")
    args = parser.parse_args()
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)

    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()

    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    public_pem = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    private_path = out / "license-private.pem"
    public_path = out / "license-public.pem"
    private_path.write_bytes(private_pem)
    public_path.write_bytes(public_pem)
    print(f"Wrote {private_path}")
    print(f"Wrote {public_path}")
    print("Set ISPF_LICENSE_PUBLIC_KEY_PEM to contents of license-public.pem on the server.")


if __name__ == "__main__":
    main()
