# ISPF P&ID Symbol Pack (ispf-pid-v1)

## License

**Apache-2.0** — original artwork by ISPF Core Contributors.

These symbols are **functional P&ID diagrams** drawn to common **ISA-5.1** / **ISO 14617**
conventions. They are **not** traced, converted, or derived from Siemens SymbolFactory,
TIA Portal, or other vendor WMF/SVG libraries.

## Generator

Built with [`tools/symbol-pack-isa`](../../../../../../tools/symbol-pack-isa):

```bash
cd tools/symbol-pack-isa && npm install && npm run build
```

## Use

- Inside ISPF SCADA mimic diagrams only.
- You may extend the pack by adding symbols to `tools/symbol-pack-isa/src/symbols.ts`
  and re-running the generator.
- Do not republish this pack as a standalone icon library unrelated to ISPF.

## Related

- [docs/en/pid-symbols-legal.md](../../../../../../docs/en/pid-symbols-legal.md)
