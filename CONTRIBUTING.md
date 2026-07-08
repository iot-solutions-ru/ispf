# Contributing to ISPF

## License

By contributing to this repository, you agree that your contributions are licensed under
the same terms as the project (**GNU AGPL v3**), and you grant the copyright holder the
rights described in [CLA.md](CLA.md) (dual licensing / commercial relicensing).

## Before a pull request

1. Sign or accept [CLA.md](CLA.md) (Contributor License Agreement).
2. Do not commit industry-specific Java, customer bundles, or secrets.
3. See [docs/en/plugins.md](docs/en/plugins.md) and [docs/en/license.md](docs/en/license.md) for boundaries.

## Driver changes

Each device driver lives in `packages/ispf-driver-*` and ships as a **driver pack** (see
[docs/en/licensed-driver-packs.md](docs/en/licensed-driver-packs.md)). After changing a driver:

```powershell
.\gradlew :packages:ispf-driver-<name>:assembleDriverPack
```

Regenerate catalog if you add a new driver module:

```powershell
python tools/generate-driver-packs-json.py
```

## Tests

```powershell
.\gradlew syncAllDriverPacks :packages:ispf-server:test
```

Tests expect driver packs in `build/driver-packs` (configured automatically).
