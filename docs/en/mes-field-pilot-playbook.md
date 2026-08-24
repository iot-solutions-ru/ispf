# MES field pilot playbook (BL-164…170 / Post-S33)

> **Status:** Lab — plant validation runbook. Hub: [doc-status.md](doc-status.md).

Repeatable **shop-floor MES** pilot on a named line using `mes-platform-production` (or site bundle). Marketplace/lab smoke is **Done**; this closes the gap: **real plant loop**, not Opcenter parity.

See [reference-mes-platform.md](reference-mes-platform.md) · [field-pilot-playbook § ready-for-field](field-pilot-playbook.md#ready-for-field-gate-policy).

**Out of scope:** live ERP (BL-169 parked) — outbox remains stub-honest.

---

## Pilot matrix

| Profile | Bundle | Typical site | Success criteria |
| ------- | ------ | -------------- | ---------------- |
| **Reference line** | `mes-platform-production` | Packaging / assembly cell | WO→dispatch→OEE→quality→batch→genealogy 7 days |
| **Site custom** | `examples/field-sites/mes-line-a01/` | Customer line IDs | Same loop with plant WO/lot IDs |

Target soak: **minimum 7 days**; log incidents in [mes-field-soak-journal.template.md](../evidence/mes-field-soak-journal.template.md).

---

## Prerequisites

- ISPF ≥ 0.9.100, `MesPlatformBootstrap` on boot
- Device admin or configurator for deploy
- Operator account for shift dashboards
- Drivers for line signals (virtual OK for playbook-ready; real PLC for field Done)

---

## Deploy (≤ 30 min)

```bash
# Local or VPS (unsigned OK when ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false)
ISPF_BASE_URL=https://ispf.example.invalid \
ISPF_DEPLOY_USER=admin ISPF_DEPLOY_PASSWORD=… \
bash deploy/tools/mes-platform-production-deploy.sh
```

**Prod VPS** (`require-signed-bundles=true`): deploy returns **403** until bundle is signed for this installation-id (`GET /api/v1/platform/installation-id`) via `tools/license-builder/sign-bundle.py`, **or** temporarily set `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false` in `/opt/ispf/ispf-server.env` and recreate the JVM container.

Manual: `POST /api/v1/applications/mes-platform-production/deploy` with [bundle.json](../../examples/mes-platform-production/bundle.json).

Operator: `?mode=operator&app=mes-platform-production`  
Hub: `root.platform.devices.mes-platform-production-hub`

---

## Daily plant loop checklist

| Step | BFF / action | Pass |
| ---- | ------------ | ---- |
| 1 | `mes_platform_listLines` | Line visible |
| 2 | Instantiate WO ([example JSON](../../examples/mes-platform-production/work-order-instantiate.example.json)) | WO in tree |
| 3 | `mes_dispatch_confirmWorkOrder` | Dispatch event |
| 4 | `mes_oee_getKpi` | KPI &gt; 0 |
| 5 | Quality dashboard SPC | Chart renders |
| 6 | `mes_batch_runPhase` | Phase advances |
| 7 | `mes_genealogy_queryByLot` | Seed lot trace |
| 8 | `mes_erp_enqueueOutbox` / poll | Stub round-trip only |

---

## Field site package

Starter checklist: [examples/field-sites/mes-line-a01/README.md](../../examples/field-sites/mes-line-a01/README.md)

Add site-specific WO/lot IDs, operator branding, and device paths under `root.platform.devices.<site>-hub` — **never** `ObjectType.DEVICE` on hub node.

---

## Sign-off (quality path)

| Claim | Proof |
| ----- | ----- |
| Lab GA smoke | `MesPlatformGaSmokeTest` green |
| Field reference | 7-day journal + named site |
| ERP integration | **Not** claimed until BL-169 named task |

---

## Related

| Doc | Purpose |
| --- | ------- |
| [mes.md](mes.md) | MES object model |
| [reference-mes-oee-walkthrough.md](reference-mes-oee-walkthrough.md) | OEE deep dive |
| [ai-field-soak-playbook.md](ai-field-soak-playbook.md) | AI deploy on same stand |
