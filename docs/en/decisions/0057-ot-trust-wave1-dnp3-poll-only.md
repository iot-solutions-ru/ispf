# ADR-0057: OT Trust Wave 1 — DNP3 stays PRODUCTION poll-only

**Status:** Accepted  
**Date:** 2026-09-05  
**Context:** Phase 25 OT Trust unparked for Wave 1. Roadmap / promotion docs incorrectly claimed BL-191 downgraded `dnp3` and `ethernet-ip` to **BETA**. Matrix code did not.

## Decision

1. **`dnp3`** remains **`PRODUCTION`** with capabilities **`POLL` only**.  
   - Read / class poll path is covered by loopback tests.  
   - `writePoint` throws “not implemented” — that is a **declared capability gap**, not a stub shell.  
   - Do **not** label it BETA solely because write is missing (same pattern as other honest POLL_ONLY PRODUCTION drivers).  
   - Implementing DNP3 write is a **separate named task** (not required to keep Wave 1 moving).

2. **`ethernet-ip`** remains **`PRODUCTION`** with **`POLL` + `WRITE` + `QUALITY`** — CIP write is implemented; do not re-mark BETA.

3. **`opc-da` / `opc-bridge`** stay **`BETA`** (shells) and remain in `TOP_20_NON_PRODUCTION_EXEMPT`.

4. **`http`** matrix capabilities must include **`WRITE`** where `HttpDeviceDriver.writePoint` is implemented (honesty: under-claiming WRITE is still a matrix bug).

5. Wave 1 field track starts with **Modbus TCP lab pilot** (fixtures in `deploy/driver-interop/`), not a customer plant, until a named site appears.

## Consequences

- Correct BL-191 “Done” language: shells → BETA + stub javadoc CI gate + no false WRITE on DNP3.  
- Residual OT Trust work = interop write smoke, Modbus soak journal, optional DNP3 write later.  
- Parked board **P-OT** moves to **In progress** under this named Wave 1 task.

## References

- [0022-driver-production-matrix](0022-driver-production-matrix.md)  
- [roadmap § Wave 1](../roadmap.md#s31-wave-1-execution-backlog)  
- [driver-interop-lab](../driver-interop-lab.md)  
- Evidence: `docs/evidence/ot-trust/`
