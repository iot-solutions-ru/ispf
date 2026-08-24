# AI generator field soak journal (BL-180)

> Copy to `docs/evidence/ai-generator/YYYY-MM-DD-<site>-journal.md` after a named-site run.

| Field | Value |
| ----- | ----- |
| Site | _e.g. ispf.iot-solutions.ru / plant-line-a01_ |
| Domain | _hvac \| mes \| scada_ |
| Date (UTC) | _YYYY-MM-DD_ |
| Integrator | _name_ |
| ISPF version | _e.g. 0.9.186_ |
| Model | _ISPF_AI_MODEL (no API key in ticket)_ |
| Evidence JSON | _link to dated `live-generator-results.json`_ |

## Daily log

| Day | functionalOk | softBudgetMet | elapsedMs | Operator spot-check | Notes |
| --- | ------------ | ------------- | --------- | --------------------- | ----- |
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

## Sign-off

- [ ] `functionalOk: true`, hub/dashboard/alert paths verified in Explorer
- [ ] Operator app opens (`?mode=operator&app=<appId>`)
- [ ] `softBudgetMet: true` OR documented soft miss (not claimed as GA)
- [ ] No manual tree edits required after generator apply

**Status:** _playbook-ready \| field-soak-in-progress \| field-evidence-attached_
