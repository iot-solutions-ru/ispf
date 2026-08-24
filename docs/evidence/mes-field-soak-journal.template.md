# MES field soak journal (BL-164…170 / Post-S33)

> Copy to `docs/evidence/mes-field/YYYY-MM-DD-<site>-journal.md`.

| Field | Value |
| ----- | ----- |
| Site / line | _e.g. packaging-line-a01_ |
| ISPF version | |
| Bundle | _mes-platform-production \| custom site bundle_ |
| Hub path | _root.platform.devices.…-hub_ |
| ERP connector | _stub only \| named ERP (BL-169 deferred)_ |

## 7-day plant loop

| Day | WO dispatch | OEE KPI | Quality SPC | Batch phase | Genealogy query | Incidents P0/P1 |
| --- | ----------- | ------- | ----------- | ----------- | --------------- | --------------- |
| 1 | | | | | | |
| 2 | | | | | | |
| … | | | | | | |
| 7 | | | | | | |

## Sign-off checklist

- [ ] `bash deploy/tools/mes-platform-production-deploy.sh` green on site
- [ ] Operator dashboards Dispatch / OEE / Quality usable by shift lead
- [ ] Work order instantiate → dispatch → complete without admin console edits
- [ ] ERP outbox: honest stub (`sent` ≠ live ERP unless BL-169 scope)
- [ ] 7 full days without open P0

**Status:** _lab-only \| field-soak \| production-reference_
