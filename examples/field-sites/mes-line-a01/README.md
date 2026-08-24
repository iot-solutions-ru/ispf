# MES field site — line A01 (Post-S33 starter)

Named-site template for **MES field pilot** ([mes-field-pilot-playbook.md](../../docs/en/mes-field-pilot-playbook.md)). Not a marketplace bundle — site-specific IDs and checklist only.

## Site profile

| Field | Example value |
| ----- | ------------- |
| Site id | `line-a01` |
| Hub device | `root.platform.devices.line-a01-hub` |
| Operator app | Extend `mes-platform-production` or clone appId `mes-line-a01` |
| WO prefix | `WO-LINE-A01-` |
| Lot prefix | `LOT-LINE-A01-` |

## Pre-flight (integrator)

- [ ] ISPF ≥ 0.9.100, MES catalog bootstrapped
- [ ] `bash deploy/tools/mes-platform-production-deploy.sh` against site URL
- [ ] Operator opens `?mode=operator&app=mes-platform-production`
- [ ] Line signals: virtual driver OK for playbook-ready; real PLC for field Done
- [ ] ERP: document stub-only unless BL-169 named task

## 7-day loop

Use [mes-field-soak-journal.template.md](../../docs/evidence/mes-field-soak-journal.template.md). Minimum daily checks:

1. Instantiate one WO from [work-order-instantiate.example.json](../mes-platform-production/work-order-instantiate.example.json) (adjust IDs)
2. Dispatch confirm + OEE KPI
3. Quality dashboard — one SPC point
4. Batch phase advance (optional day 3+)
5. Genealogy query on seed lot

## Customization

| Customize | Where |
| --------- | ----- |
| WO/lot IDs | Instantiate JSON + tree variables |
| OEE targets | Hub variables under site hub |
| Dashboards | Clone from production bundle paths |
| Site plugin | Optional `plugins/mes-site-<customer>/` for BFF-only extensions |

**Hub rule:** hub node uses variables + BFF — not `ObjectType.DEVICE` (see AGENTS.md).

## Related

- [mes-platform-production/](../mes-platform-production/) — certification bundle
- [reference-mes-platform.md](../../docs/en/reference-mes-platform.md)
