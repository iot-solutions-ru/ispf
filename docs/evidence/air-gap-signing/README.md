# Air-gap signing evidence (RSA Done / Cosign Lab Done)

> Playbook: [air-gap deployment](../../en/air-gap-deployment.md) § Commercial licensing · § Publisher signatures (Cosign).

App-bundle **RSA** signing via `tools/license-builder/sign-bundle.py` remains the in-repo deploy gate. **Cosign** tooling (CI smoke + release keyless GHCR) is **Lab Done**; a dated **customer** air-gap host transcript is still **Gap**.

## Claim boundary

| Claim | Today |
|-------|--------|
| RSA `license` block on app manifests (`sign-bundle.py` / jar helper) | **Done** |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` + public PEM verify on deploy | **Done** (prod + air-gap compose default `true`) |
| Demostand RSA smoke | **Done** — [mes-field signed bundles](../mes-field/2026-08-25-ispf-vps-signed-bundles.md), `deploy/tools/vps-enable-signed-bundles.sh` |
| Air-gap compose / apply default require-signed | **Done** — `deploy/docker-compose.air-gap.yml` (override `false` for lab) |
| `CHECKSUMS.sha256` in air-gap tar | **Done** as corruption check — **not** a publisher signature |
| Cosign CI smoke (keyful blob + local-registry image) | **Lab Done** — `deploy/tools/air-gap-cosign-smoke.sh` + `.github/workflows/cosign-smoke.yml` |
| Cosign keyless sign on GHCR release digests | **Lab Done** — `.github/workflows/release.yml` |
| Optional `ISPF_COSIGN_VERIFY` on `air-gap-apply.sh` | **Lab Done** |
| Dated Cosign verify log on a **customer** air-gap host | **Gap** — add `docs/evidence/air-gap-signing/<site>-<date>/` only after a real run |

## Lab / CI smoke

```bash
bash deploy/tools/air-gap-cosign-smoke.sh
# → build/cosign-smoke/cosign-verify.txt
```

CI uploads the transcript as artifact `cosign-lab-smoke`. Copy a redacted run under `docs/evidence/air-gap-signing/ci-<date>/` when refreshing this folder after a green workflow.

## Customer host runbook (still Gap until archived)

When a vendor release process signs Docker digests with Cosign (keyful or keyless), record on the air-gap host:

```text
docs/evidence/air-gap-signing/<id>/
  README.md           # date, images verified, key identity (no private keys)
  cosign-verify.txt   # redacted `cosign verify` transcript
```

Until that folder exists, scorecard must **not** claim field Cosign. Do not present CI smoke as a plant host verify.

## Lab override (RSA)

```bash
export ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false
export ISPF_LICENSE_ENFORCE=false
bash deploy/air-gap-apply.sh /path/to/ispf-airgap-<version>.tar.gz
```

Do not present lab override as production air-gap posture.
