# ERP-MES APS

Finite planning UI on [`erp-mes-core`](../erp-mes-core/) ≥ **2.1.0**.

```bash
python examples/erp-mes-aps/generate_bundle.py
```

Provides: job board mirror, capability conflict list (`emc_aps_listConflicts`), soft replan
(`emc_joborder_updatePlan`), plan freeze table. No MIP/CP solver in v1.
