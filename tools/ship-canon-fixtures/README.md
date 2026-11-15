# Shared ship-canon fixtures (ADR-0060)

Tiny bundle JSON fragments consumed by:

- `tools/ispf-cli` (`findLogicHostDeviceIssues` / `--strict`)
- `tools/marketplace-catalog/ship-canon-gate.test.mjs`
- `packages/ispf-server` `ShipCanonGoldenFixturesTest` (`BundleManifestValidator`)

Keep fixtures minimal. Expected codes live in `expect.json`.
