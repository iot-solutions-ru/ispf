> **Language:** Canonical English. Russian edition: [ru/driver-promotion.md](../ru/driver-promotion.md).

# Driver promotion process

> **Status:** Stable — PRODUCTION + ready-for-field. Hub: [doc-status.md](doc-status.md).

How to move drivers from **stub** / **beta** to **production** (Phase 3.2).

## Labels

| `maturity` | Meaning |
|------------|----------|
| `PRODUCTION` | Typical scenarios, documented config, tests |
| `BETA` | Working protocol with limitations (platform, auth, partial stack) |
| `STUB` | Connectivity shell — not for production telemetry |

Label is set in `DriverMaturityRegistry` (server) and returned in `GET /api/v1/drivers`.

## Promotion checklist

1. Implement poll/read (or write, when declared) in `ispf-driver-*` module.
2. Add unit/integration tests for point parser and happy path.
3. Update description in `DriverMetadata` and section in [drivers](drivers.md).
4. Change entry in `DriverMaturityRegistry`.
5. If needed — demo device / model in bootstrap.

## Status (July 2026, Phase 25 BL-140)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `iec104` | BETA | **PRODUCTION** | Loopback vs `iec104-server`; write commands |
| `dnp3` | BETA | **PRODUCTION** (poll only) | Class 0/1/2/3 poll; **write not implemented** — field write = not ready |
| `dlms` | BETA | **PRODUCTION** | Gurux read/write; auth NONE |
| `ethernet-ip` | BETA | **PRODUCTION** | CIP session registration + tag path loopback |
| `opc-da` | BETA | **PRODUCTION** (shell) | Connectivity shell + parser tests — **not** ready-for-field DA |
| `opc-bridge` | BETA | **PRODUCTION** (shell) | Bridge point mapping + parser tests — full OPC via external bridge |

**Policy:** connectivity shells and read-only masters must not be sold as field-ready PRODUCTION. Use [Ready-for-field](#ready-for-field-field-pilots) below; scorecard OT dimension tracks these gaps.

## Status (June 2026)

| driverId | Was | Now | Note |
|----------|------|-------|------------|
| `dnp3` | STUB | **BETA** | Class 0/1/2/3 poll via `io.stepfunc:dnp3`; write not implemented |
| `cwmp` | STUB | **PRODUCTION** | Inform + ACS `GetParameterValues`; TR-069 acceptance tests |
| `flexible` | BETA | **PRODUCTION** | TCP/UDP request/response |
| `gps-tracker` | BETA | **PRODUCTION** | GPS/M2M TCP server |
| `corba` | STUB | **BETA** | IIOP TCP reachability + point parser tests |
| `ethernet-ip` | STUB | **BETA** | CIP session registration + tag path mapping |
| `opc-da` | STUB | **BETA** | DCOM/TCP connectivity shell + parser tests |
| `opc-bridge` | STUB | **BETA** | Bridge point mapping + parser tests; full OPC stack via external bridge |
| `vmware` | STUB | **BETA** | vSphere API point parser + connectivity shell |
| `smi-s` | STUB | **BETA** | SMI-S CIM point parser + connectivity shell |

Remaining stub drivers require native stack or commercial pack — promotion only on specific request ([licensed-driver-packs](licensed-driver-packs.md)).

## Ready-for-field (field pilots)

**Not automatic** when `maturity: PRODUCTION` or lab interop is green. A driver/scenario is **ready for field** only after:

1. **Named field implementation task** — site, protocol, integrator ticket, scope to implement or harden the driver for that deployment.
2. Lab dry-run green for that scenario ([field-pilot-playbook](field-pilot-playbook.md)).
3. **7-day soak** + customer OT sign-off.

Until (1): status is **playbook-ready** only. See BL-140 (Partial) and [quality path Wave 1](roadmap.md#quality-path-to-done).
