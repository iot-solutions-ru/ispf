# FIELD C1 evidence folder (template)

> **Status:** Ops template — **no driver is FIELD in the matrix until a named customer plant pack is complete.**  
> **Today:** `DriverMaturity.FIELD` count = **0** (C1 open). Lab VLAN soaks and marketplace REAL installs do **not** fill this folder for matrix promotion.

Canonical gates: [ADR-0067 §1b](../../en/decisions/0067-field-certified-maturity.md), [field C1 checklist](../../en/field-c1-evidence-checklist.md), [field-pilot-playbook](../../en/field-pilot-playbook.md).

## When to create a subfolder

Create **`docs/evidence/field-c1/<pilot-id>/`** only when all of the following exist:

1. **Named customer plant** (not internal lab VLAN only) — site name, OT contact, integrator ticket.
2. **Field task scope** — protocol, driver id(s), capabilities claimed (READ / WRITE / SUBSCRIBE).
3. **Playbook alignment** — pilot id matches [field-pilot-playbook](../../en/field-pilot-playbook.md) or a documented customer variant.

Until then, use [ot-trust](../ot-trust/) lab artifacts or [field-pilot-playbook](../../en/field-pilot-playbook.md) kickoff packs — **do not** label matrix rows `FIELD`.

## Per-pilot layout (copy this tree)

```text
docs/evidence/field-c1/<pilot-id>/
  README.md                 # one-page summary + links (required)
  site-intake.md              # C1: plant, VLAN, endpoints, ticket, contacts (required)
  device-config.md            # C2: applied driver config vs planned (required)
  validation-day0.md          # connect / read / write / historian / HMI (required)
  soak.journal.md             # ≥7 day dated entries, incidents (required)
  sign-off.md                 # OT / site-owner acceptance (required)
  artifacts/                  # optional: exports, screenshots, redacted configs
```

### `README.md` (instance) — minimum front matter

| Field | Example |
|-------|---------|
| `pilot-id` | `acme-tankfarm-modbus-2026` |
| `driver-id` | `modbus-tcp` |
| `site-type` | `customer-plant` (not `lab-vlan`) |
| `soak-start` / `soak-end` | ISO dates, end − start ≥ 7 days |
| `matrix-promotion` | `pending` until PR updates `FIELD_EVIDENCE_PATHS` |

## Matrix promotion (after evidence)

1. Complete every item on [field-c1-evidence-checklist.md](../../en/field-c1-evidence-checklist.md).
2. Add a resolvable path in `DriverProductionMatrix` `FIELD_EVIDENCE_PATHS` (code change — not docs-only).
3. CI must resolve the evidence file; **never** promote FIELD from this README alone.

## Related (not substitutes)

| Lane | Path | Matrix FIELD? |
|------|------|----------------|
| BL-140 lab Modbus soak | [ot-trust/pilot1-modbus-plant.checklist.md](../ot-trust/pilot1-modbus-plant.checklist.md) | No — internal lab |
| Marketplace REAL pilots | [marketplace-pilots](../../en/marketplace-pilots.md) | No — solution lane ([0067 §1b](../../en/decisions/0067-field-certified-maturity.md)) |
| OT Trust codec promotions | [ot-trust/](../ot-trust/) | No — PRODUCTION lab CI only |
