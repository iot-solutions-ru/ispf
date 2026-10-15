# Cosign lab gate — 2026-10-15

**Status:** Lab Done (tooling). Customer-host Gap unchanged.

## What shipped

| Piece | Path |
|-------|------|
| Keyful CI smoke | `deploy/tools/air-gap-cosign-smoke.sh` |
| Workflow | `.github/workflows/cosign-smoke.yml` |
| Release keyless sign | `.github/workflows/release.yml` (after GHCR push) |
| Optional apply verify | `ISPF_COSIGN_VERIFY` in `deploy/air-gap-apply.sh` |

## Commands

```bash
bash deploy/tools/air-gap-cosign-smoke.sh
```

CI artifact: `cosign-lab-smoke` → `cosign-verify.txt`.

## Non-claims

- No scorecard air-gap/openness numeric bump until a dated **customer** host folder exists.
- CI smoke ≠ plant air-gap Cosign verify.
