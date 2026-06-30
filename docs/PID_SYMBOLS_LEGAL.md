# P&ID symbol pack — legal status

**Status:** Option **B** implemented (2026-06) — original ISA/ISO functional artwork.

Pack: `apps/web-console/src/scada/symbols/packs/ispf-pid-v1/`  
Generator: [`tools/symbol-pack-isa`](../tools/symbol-pack-isa)  
License: **Apache-2.0** (ISPF Core Contributors)

## Current model

| Item | Status |
|------|--------|
| Vendor WMF/SymbolFactory import | **Removed** — `tools/symbol-import/` deprecated |
| Symbol geometry | **Original** — drawn in `tools/symbol-pack-isa/src/symbols.ts` (64×64, CSS vars) |
| Conventions | ISA-5.1 instrument tags (PI, TI, FT, …), functional valve/equipment shapes |
| Count | ~58 symbols (expand by editing generator, not vendor import) |

Regenerate:

```bash
cd tools/symbol-pack-isa && npm install && npm run build
```

## Why this is lower risk than vendor import

Functional/industry-standard P&ID shapes (gate valve wedge, instrument bubble, tank outline)
express **process meaning**, not vendor-specific decorative artwork. ISPF symbols are
**authored from conventions**, not traced from Siemens WMF paths.

Counsel may still review that specific renderings do not closely mimic a proprietary catalog.

## Counsel checklist (optional sign-off)

- [ ] Sample review: gate valve, control valve, PI bubble, vertical tank, heat exchanger.
- [ ] Confirmed no vendor WMF/SVG in git history for current pack files (post-regeneration).
- [ ] Approved Apache-2.0 attribution in `LICENSE.md` and release bundle.
- [ ] Approved customer-facing wording if Enterprise EULA references SCADA symbols.

**Sign-off:** _______________ Date: ___________

## Deprecated (do not use)

`tools/symbol-import/` — WMF pipeline from WinCC/TIA. **Forbidden** for ISPF releases.

## Related

- [LICENSE.md](../apps/web-console/src/scada/symbols/packs/ispf-pid-v1/LICENSE.md)
- [LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md)
- [tools/symbol-pack-isa/README.md](../tools/symbol-pack-isa/README.md)
