# ADR-0008: Federation topology

## Status

Accepted (2026-06-21)

## Context

Distributed ISPF installations (hub + edge) require a single model: stable object path, proxy read/write, catalog sync, and production bind. Spike PF-13 is implemented; topology and boundaries must be recorded.

## Decision

1. **Object path ≠ endpoint** — `root.platform.devices.x` is a logical identifier; peer URL is stored in `federation_peers`, not in the path.
2. **Hub–spoke topology** — hub holds the peer registry; edge registers via inbound registration code or outbound tunnel agent (`/ws/federation/tunnel`).
3. **Two integration modes:**
   - **Catalog sync** — bulk import of remote catalog under `root.platform.federation.{peer}.*` for overview.
   - **Federation bind** — overlay remote object onto a **local** operator path (`federationProxy`, `federationPeerId`, `federationRemotePath`); local driver stops until unbind.
4. **Sync RPC for operations** — proxy read/write/history/dashboard through hub HTTP API; not a replacement for event bus between instances.
5. **Auth** — service account Bearer on peer (`federation-token`, `remote-token`); tokens in peer registry with lifecycle refresh (V29).
6. **Out of scope** — auto-merge of conflicting trees, federation billing/quota, single global namespace without explicit bind.

## Consequences

- Implementation: `FederationProxyService`, `FederationPeerService`, tunnel WebSocket — see [federation.md](../federation.md).
- WebSocket fan-out notifies subscribers on federated paths; full replica consistency — via NATS only within one platform cluster.
- Dashboard widget paths remapped to federated prefix when proxying layout.

## Related

- REQ-PF-13 — [roadmap.md](../roadmap.md), [federation.md](../federation.md)
- [messaging.md](../messaging.md) — sync vs async
