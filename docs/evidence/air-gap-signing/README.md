# Air-gap signing evidence (RSA Done / Cosign Gap)

> **Status:** claim-boundary stub.  
> Playbook: [air-gap deployment](../../en/air-gap-deployment.md) § Commercial licensing · § Publisher signatures (Cosign).

Do not treat Cosign as shipped. App-bundle **RSA** signing via `tools/license-builder/sign-bundle.py` is the in-repo gate.

## Claim boundary

| Claim | Today |
|-------|--------|
| RSA `license` block on app manifests (`sign-bundle.py` / jar helper) | **Done** |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` + public PEM verify on deploy | **Done** (prod + air-gap compose default `true`) |
| Demostand RSA smoke | **Done** — [mes-field signed bundles](../mes-field/2026-08-25-ispf-vps-signed-bundles.md), `deploy/tools/vps-enable-signed-bundles.sh` |
| Air-gap compose / apply default require-signed | **Done** — `deploy/docker-compose.air-gap.yml` (override `false` for lab) |
| `CHECKSUMS.sha256` in air-gap tar | **Done** as corruption check — **not** a publisher signature |
| Cosign / sigstore image or archive signatures | **Gap** — not in this repository |
| Dated Cosign verify log on a customer air-gap host | **Gap** — add `docs/evidence/air-gap-signing/<site>-<date>/` only after a real run |

## Optional Cosign runbook (outside this repo)

When a vendor release process signs Docker digests with Cosign (keyful or keyless), record on the air-gap host:

```text
docs/evidence/air-gap-signing/<id>/
  README.md           # date, images verified, key identity (no private keys)
  cosign-verify.txt   # redacted `cosign verify` transcript
```

Until that folder exists, scorecard Cosign stays **Gap**. ISPF does not ship a Cosign binary or CI verify job.

## Lab override

```bash
export ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false
export ISPF_LICENSE_ENFORCE=false
bash deploy/air-gap-apply.sh /path/to/ispf-airgap-<version>.tar.gz
```

Do not present lab override as production air-gap posture.
