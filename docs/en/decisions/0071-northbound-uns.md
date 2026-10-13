# ADR-0071: Northbound / UNS scope

## Status

**Accepted** (2026-10-13) — scopes Unified Namespace / northbound claims to shipped drivers; no new product surface in this ADR.

## Context

ISPF already exposes industrial northbound paths as **DEVICE drivers**: Sparkplug B (`ispf-driver-sparkplug-b`) and OPC UA server (`ispf-driver-opcua-server`) are lab **PRODUCTION**. Marketing “UNS out of the box” without a decision record invites oversell (full Primary Host, enterprise address-space codegen, broker-as-product).

## Decision

### 1. What ISPF claims today

| Path | Status | Honesty |
|------|--------|---------|
| Sparkplug B edge/host **subset** (NBIRTH/DBIRTH/DDATA/DCMD as implemented) | Lab PRODUCTION driver | Not full Sparkplug Host certification |
| OPC UA server under `Objects/IspfVariables/…` | Lab PRODUCTION driver | Not a full companion-spec product suite |
| Tree as the system of record | Hard rule ([0001](0001-app-platform-boundary.md)) | UNS topics/nodes are **projections**, not a second master |

### 2. What is out of scope until a named task

- Sparkplug **Primary Host** certification / TCK claim
- Auto-mapping every blueprint → Sparkplug template / OPC UA ObjectType pack without an application bundle
- Replacing MQTT UNS brokers (HiveMQ, etc.) — ISPF integrates, does not become the broker product

### 3. Promotion path

Northbound capabilities stay **lab PRODUCTION** until a named plant gate and conformance artifacts exist ([field-pilot-playbook](../field-pilot-playbook.md); FIELD/CERTIFIED tiers land with the open ADR-0067 stack).

## Consequences

- Docs: [drivers](../drivers.md), OT Trust evidence.
- Follow-up code only under a named Host/TCK or blueprint→UA generator task.

## Related

- [0022-driver-production-matrix](0022-driver-production-matrix.md), [0001-app-platform-boundary](0001-app-platform-boundary.md)
