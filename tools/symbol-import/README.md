# P&ID symbol import pipeline (DEPRECATED)

**Do not use for production packs.** This pipeline imported Siemens WinCC / TIA SymbolFactory
WMF graphics — **removed from ISPF** for license compliance.

## Replacement

Use the original ISA/ISO symbol generator:

```bash
cd tools/symbol-pack-isa && npm install && npm run build
```

See [tools/symbol-pack-isa/README.md](../symbol-pack-isa/README.md) and
[docs/en/pid-symbols-legal.md](../../docs/en/pid-symbols-legal.md).

## Legacy scripts

The WMF import scripts remain in this folder for reference only. They must not be run
against vendor archives for redistribution into ISPF releases.
