# ADR-0055: Platform gaps for IT infrastructure monitoring (NMS) solutions

## Status

**Proposed** (2026-08-06) — Driven by acceptance of `it-infra-monitoring` (Трасса М11 / Aggregate-class NMS) and companion ui-pack `m11-monitor-ui` ([PR #69](https://github.com/iot-solutions-ru/ispf/pull/69)). Most NMS features are already expressible as bundle + existing drivers; this ADR lists **platform / driver** work still required for full TZ acceptance.

## Context

Dogfood solution: application `it-infra-monitoring` + site plugins (inventory / topology / integrations) + React MMI ui-pack. Gap analysis against ТЗ «Мониторинг М11» and AggreGate-class NMS showed:

| Area | Today on ISPF | Gap |
|------|---------------|-----|
| SNMP / ICMP / trap / syslog / sFlow | Drivers + ingress packs | Config only |
| Schedules / reports / historian | Bundle + platform | OK (interval schedules) |
| SSH | `ispf-driver-ssh` **v0.1 read-only** (`writePoint` rejected) | Config **rollback / apply** needs write path |
| OPC DA → CMS | `ispf-driver-opc-da` **stub** (placeholder values; DCOM note) | Production bridge or OPC UA gateway |
| Alarm notify SMS | Webhook / email-relay only | No first-class SMS adapter |
| Report e-mail | `schedules[].action.emailTo` | Must wire through `NotificationDispatchService` + `ispf.notifications.email-relay-url` (document + dogfood) |
| Bundle schedules | `BundleSchedule.intervalMs` only (primitive `long`) | Cron / calendar schedules not in bundle schema |
| Windows soft inventory | HOST-RESOURCES / SSH scripts | No WMI / WinRM driver |
| Vendor storage (OceanStor) | Generic HTTP/SNMP | Optional vendor blueprint / HTTP pack |
| Hosted SPA | ADR-0054 | Ensure `/apps/<appId>/` on prod stands (not console SPA fallback) |
| Signed deploy | `require-signed-bundles=true` | Partner signing workflow for ITM releases |

Acceptance case must not push industry Java into `ispf-server` ([0001](0001-app-platform-boundary.md)). Gaps are **generic primitives** (drivers, notification channels, schedule schema, ui-pack hosting).

## Decision

Treat the following as **platform epics** (ordered). Solution bundles may ship stubs/metadata meanwhile; production TZ sign-off waits on P0–P1.

### P0 — Blocking for NMS TZ acceptance

1. **SSH write / config apply (driver)**  
   - Extend `ispf-driver-ssh` beyond read-only: gated `writePoint` / `execWrite` with allow-listed commands, confirm/timeout, audit log.  
   - Keep default **read-only** for existing packs; opt-in `writeEnabled` + command policy in driver config.  
   - Acceptance: collect → baseline → apply rollback snippet on lab switch (dry-run + commit modes).

2. **OPC Classic production path**  
   - Replace stub reads with either:  
     - **A)** Windows side-car / DCOM bridge (HTTP or TCP JSON over `proxyPort`), or  
     - **B)** Documented **OPC UA** mapping path + `ispf-driver-opc-ua` (if preferred over Classic).  
   - Keep `progId` / item id mapping; quality + timestamp in `DataRecord`.  
   - Acceptance: write warning/alarm tags consumed by CMS lab (or UA simulator).

3. **Hosted ui-pack on production ingress**  
   - Confirm ADR-0054 filter is active on `ispf.iot-solutions.ru` (today `/apps/*` may fall through to console index).  
   - Acceptance: `GET /apps/it-infra-monitoring/` serves ui-pack `index.html`, not web-console shell.

### P1 — Required for operational parity

4. **Schedule e-mail delivery**  
   - Ensure `run_report` schedule action with `emailTo` uses `NotificationDispatchService` (or document required correlator rule).  
   - Dogfood `ispf.notifications.email-relay-url` on staging.

5. **SMS / messenger notify channel**  
   - Generic outbound adapter: HTTP SMS gateway (same SSRF rules as webhooks) — e.g. `SEND_SMS` correlator action or `ispf.notifications.sms-relay-url`.  
   - No carrier SDK inside core; relay only.

6. **Bundle schedule cron (optional field)**  
   - Extend `BundleSchedule` with optional `cron` (Quartz/cron) **or** keep interval-only and document that calendar jobs use platform schedules API. Prefer adding optional `cron` + `intervalMs=0` sentinel without breaking Jackson primitive mapping.

### P2 — Nice-to-have / site-specific

7. **WMI / WinRM driver pack** (or sanctioned SSH+PowerShell profile) for software inventory.  
8. **OceanStor / SAN HTTP blueprint** example under `examples/` (not core).  
9. **Network discovery automation** helper (ICMP/SNMP sweep as reusable function template).  
10. **Partner license-builder runbook** for ITM signed releases (`installationId` binding).

### Non-goals

- Embedding M11 React sources into `apps/web-console`.  
- Native RDP/ASDM/vCenter embedding (deep-links only).  
- Replacing operator dashboards — ui-pack remains primary MMI.

## Consequences

- ITM / M11 marketplace package can claim TZ coverage once P0 lands; P1 closes NOC notify/report loops.  
- SSH write increases blast radius — require policy, roles, and audit (align with [0039](0039-unified-alarm-architecture.md) notify paths).  
- OPC DA remains Windows-sensitive; prefer bridge over embedding DCOM in Linux JVM.

### Risks

- SSH write misuse → network outage: mitigate with allow-list + two-person confirm for production profiles.  
- OPC bridge ops burden on Windows hosts.  
- Cron in bundles duplicates platform schedule objects — keep one source of truth.

## Acceptance checklist

- [ ] SSH write policy + lab rollback demo  
- [ ] OPC DA bridge **or** UA path with CMS tag round-trip  
- [ ] `/apps/it-infra-monitoring/` serves `m11-monitor-ui` on prod  
- [ ] Schedule report e-mail via configured relay  
- [ ] SMS relay action in correlator (lab)  
- [ ] Docs: drivers.md + notifications + ITM MARKETPLACE note updated  
- [ ] Dogfood: deploy signed or stepwise ITM 1.1.x on staging

## Related

- [0001-app-platform-boundary](0001-app-platform-boundary.md)  
- [0022-driver-production-matrix](0022-driver-production-matrix.md)  
- [0039-unified-alarm-architecture](0039-unified-alarm-architecture.md)  
- [0054-hosted-ui-packs](0054-hosted-ui-packs.md)  
- [drivers](../drivers.md), [marketplace](../marketplace.md)  
- ITM ui-pack PR: https://github.com/iot-solutions-ru/ispf/pull/69  
- Driver stubs: `packages/ispf-driver-ssh` (`writePoint` read-only), `packages/ispf-driver-opc-da` (placeholder)
