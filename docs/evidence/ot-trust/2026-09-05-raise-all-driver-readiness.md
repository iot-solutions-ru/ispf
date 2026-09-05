# OT Trust — raise driver readiness (all 162)

> Date: 2026-09-05  
> Honesty: raises **lab readiness ladder** only — does **not** claim 162/162 PRODUCTION or field OT Trust Done.

## Before → after

| Bucket | Before | After |
|--------|--------|-------|
| `READY_LAB` (PRODUCTION + loopback) | 62 | **62** |
| `STUB_LAB` (stub + pack contract) | 0 | **97** |
| bare `STUB` | 97 | **0** |
| `SHELL_BETA` (`opc-da` / `opc-bridge`) | 2 | **2** |
| `PARTIAL` (`corba`) | 1 | **1** |
| Audit FAIL / WARN | 0 / 3 | **0 / 0** |

## What changed

1. **stub-kit v0.2** (`ProtocolStubDeviceDriver`): TCP probe on read + **in-memory write loopback** for console/CI contracts. Maturity remains **`STUB`**.
2. **Pack contract tests** for all 97 catalog stubs via `tools/driver-stubs/raise-stub-readiness.py` (also wired into the pack generator).
3. Audit label **`STUB_LAB`** when a stub extends the kit **and** has a pack `*Test.java`.
4. Cleared false-positive `WRITE_UNDERCLAIM` on ingress drivers (write-window previously matched `.submit(` in neighbor methods).

## Explicit non-claims

- **`STUB_LAB` ≠ protocol codec** and ≠ plant readiness.
- **`opc-da` / `opc-bridge` / `corba`** stay BETA shells until real stacks land.
- Competitive OT scorecard still waits on BL-140 field pilots + soak journals.

## Re-run

```bash
python3 tools/driver-stubs/raise-stub-readiness.py
python3 tools/driver-readiness-audit.py \
  --md docs/evidence/ot-trust/driver-readiness.md \
  --json docs/evidence/ot-trust/driver-readiness.json \
  --fail-on-findings
./gradlew :packages:ispf-driver-stub-kit:test
# optional: all stub pack contracts
# ids=$(python3 -c "import json; print(' '.join(':packages:ispf-driver-'+i+':test' for i in json.load(open('packages/ispf-server/src/main/resources/driver-pack/protocol-stub-ids.json'))['driverIds']))")
# ./gradlew $ids
```
