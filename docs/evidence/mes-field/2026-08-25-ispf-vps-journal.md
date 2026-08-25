# MES field soak journal (BL-164…170 / Post-S33)

| Field | Value |
| ----- | ----- |
| Site / line | ispf.iot-solutions.ru / LINE-A01 (lab VPS) |
| ISPF version | 0.9.186 |
| Bundle | `mes-platform-production` 1.1.0 |
| Hub path | `root.platform.devices.mes-platform-production-hub` |
| ERP connector | stub outbox only (BL-169 deferred) |

## Lab / day-0 checks (2026-08-24…25)

| Check | Result | Notes |
| ----- | ------ | ----- |
| Deploy | OK | **signed gate ON** (`REQUIRE_SIGNED_BUNDLES=true`); see [signed-bundles note](./2026-08-25-ispf-vps-signed-bundles.md) |
| `mes_platform_listLines` | OK | LINE-A01 |
| `mes_oee_getKpi` | OK | OEE 85% after `CAST(? AS uuid)` fix |
| Dispatch / SPC / batch / outbox | **8/8 PASS** | [2026-08-25-ispf-vps-ga-smoke.json](./2026-08-25-ispf-vps-ga-smoke.json) (`mes-platform-ga-smoke.sh`) |

## 7-day plant loop

| Day | WO dispatch | OEE KPI | Quality SPC | Batch phase | Genealogy query | Incidents P0/P1 |
| --- | ----------- | ------- | ----------- | ----------- | --------------- | --------------- |
| 0 (lab) | PASS | PASS | PASS | PASS | n/a | none |
| 1–7 | _pending real line_ | | | | | |

## Sign-off checklist

- [x] `bash deploy/tools/mes-platform-production-deploy.sh` green on site
- [x] Operator UI `mes-platform-production` HTTP 200
- [x] GA smoke (listLines, OEE, dispatch, SPC, batch, ERP outbox) archived
- [x] ERP outbox: honest stub (`sent` ≠ live ERP)
- [ ] 7 full days without open P0 — **requires plant line** (not this VPS)

**Status:** lab-only (VPS production-reference bundle + GA smoke); field-soak open for named plant
