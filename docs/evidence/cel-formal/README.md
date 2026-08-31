# CEL formal verification evidence

| File | Site | Result |
| ---- | ---- | ------ |
| [2026-08-31-ispf-vps-0.9.202-verify-smoke.json](./2026-08-31-ispf-vps-0.9.202-verify-smoke.json) | ispf.iot-solutions.ru @ **0.9.202** | Tautology / unsat rejected; normal validate + equivalence passed |
| [2026-08-30-ispf-vps-0.9.195-verify-smoke.json](./2026-08-30-ispf-vps-0.9.195-verify-smoke.json) | ispf.iot-solutions.ru @ **0.9.195** | Tautology / unsat rejected; normal validate + equivalence passed; includes `verify-equivalence` |
| [2026-08-30-ispf-vps-0.9.192-verify-smoke.json](./2026-08-30-ispf-vps-0.9.192-verify-smoke.json) | ispf.iot-solutions.ru @ **0.9.192** | Tautology / unsat rejected; normal `self.status == "FAULT"` passed (historical) |

Re-run: `BASE_URL=… ISPF_USERNAME=… ISPF_PASSWORD=… python3 deploy/tools/demostand-expressions-verify-smoke.py --out docs/evidence/cel-formal/…json`

See [expression-language § Formal verification](../../en/expression-language.md#formal-verification-adr-0055) and [ADR-0055](../../en/decisions/0055-cel-formal-verification-ai-gate.md).
