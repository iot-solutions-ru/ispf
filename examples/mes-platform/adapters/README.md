# IRS / .NET bridge skeleton (lab)

This folder documents how the customer’s existing IRS/MES (.NET 10, React 18, DIRS/MSSQL, Kafka, corporate bus) can call the ISPF plan-execution contour **without replacing** their stack.

Live plant connectors (CUTE, CIP, AVEVA, Kafka topics) are **not** implemented here. The lab side is BFF on hub:

`root.platform.singleton-blueprints.mes-platform-hub-v1`

## Pattern

```text
IRS module / bus adapter
  → HTTP POST /api/v1/bff/invoke  (or Kafka consumer → same invoke)
  → mes_plan_* / mes_plan_if*
  → mes_exec_* tables + mes_erp_outbox / mes_exec_inbox
  → optional: IRS pulls outbox via mes_plan_listOutbox and forwards to SPP
```

## Suggested ownership

| Side | Owns |
|---|---|
| IRS (.NET) | Auth to corporate IdP, Kafka consume/produce, DIRS reads, UI already in React |
| ISPF `mes-platform` | Official PUB, snapshot, shop accept/reject, plan–fact, tails, AM gates |
| SPP | Candidate calculation (IF-04), consuming IF-01…03 / 05 / 07 / 10 / 11 |

## Minimal bridge operations

See [docs/en/mes-plan-interfaces.md](../../../docs/en/mes-plan-interfaces.md).

Example invoke body: [`irs-bridge.example.json`](irs-bridge.example.json).

## What to bring when wiring live IF

1. Topic names and message schemas for IF-04 / IF-08 inbound.
2. Outbound route for IF-05 / IF-07 (SPP endpoint or bus contract).
3. Mapping of corporate Asset ID ↔ `mes_exec_asset.asset_id`.
4. Test credentials for a non-prod ISPF node (not production MFA bypass).
