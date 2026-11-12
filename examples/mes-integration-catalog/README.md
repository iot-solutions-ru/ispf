# MES Integration Catalog

B2MML envelope helpers and connector profiles for Level 4 ERP exchange on
[`erp-mes-core`](../erp-mes-core/) Part 5 outbox (`emc_erp_*`). Closes **BL-169** 1C path.

```bash
python examples/mes-integration-catalog/generate_bundle.py
```

- `1c-http` — ready profile (sandbox ACK via `emc_erp_pollOutbox` + transport log)
- `sap-idoc` — ready profile (sandbox ACK until a plant endpoint is set)
- `b2mml` — ISA-95 ProductionSchedule on the platform outbox
- `mes_b2mml_toXml` / `fromXml` — JSON↔B2MML-ish envelope
