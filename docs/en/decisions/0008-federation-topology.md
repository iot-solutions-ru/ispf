# ADR-0008: Federation topology

## Status

**Accepted** (2026-06-21) — hub–spoke **sync RPC** and bind/catalog model.  
Async edge durability and fleet rollout are **not** this ADR — see [0072-edge-fleet-rollout](0072-edge-fleet-rollout.md).

## Context

Distributed ISPF installations (hub + edge) require a single model: stable object path, proxy read/write, catalog sync, and production bind. Spike PF-13 is implemented; topology and boundaries must be recorded.

## Decision

1. **Object path ≠ endpoint** — `root.platform.devices.x` is a logical identifier; peer URL is stored in `federation_peers`, not in the path.
2. **Hub–spoke topology** — hub holds the peer registry; edge registers via inbound registration code or outbound tunnel agent (`/ws/federation/tunnel`).
3. **Two integration modes:**
   - **Catalog sync** — bulk import of remote catalog under `root.platform.federation.{peer}.*` for overview.
   - **Federation bind** — overlay remote object onto a **local** operator path (`federationProxy`, `federationPeerId`, `federationRemotePath`); local driver stops until unbind.
4. **Sync RPC for operations** — proxy read/write/history/dashboard through hub HTTP API; **not** a replacement for an event bus between sites.
5. **Auth** — service account Bearer on peer (`federation-token`, `remote-token`); tokens in peer registry with lifecycle refresh (V29).
6. **Out of scope here** — auto-merge of conflicting trees, federation billing/quota, single global namespace without explicit bind, Message-oriented Middleware (MoM) between sites, store-and-forward, multi-site GitOps fleet.

### Honesty — what this ADR owns vs neighbors

| Concern | Owner |
|---------|--------|
| Hub–spoke registry, tunnel, bind, sync proxy | **This ADR (0008)** — **Done** |
| Intra-cluster live/structure sync (NATS) | [0028](0028-horizontal-active-active-cluster.md)–[0030](0030-cluster-config-structure-replica-sync.md) |
| Edge profile / S&F / fleet GitOps lanes | [0072](0072-edge-fleet-rollout.md) — S&F Partial; fleet GitOps not claimed |
| Per-app Git Connect/Pull/Push→PR | [0063](0063-application-git-lifecycle.md) (≠ site fleet) |

Do **not** claim store-and-forward or N-site canary rollout from 0008 alone.

## Consequences

- Implementation: `FederationProxyService`, `FederationPeerService`, tunnel WebSocket — see [federation](../federation.md).
- WebSocket fan-out notifies subscribers on federated paths; full replica consistency — via NATS only within one platform cluster.
- Dashboard widget paths remapped to federated prefix when proxying layout.

## Related

- REQ-PF-13 — [roadmap](../roadmap.md), [federation](../federation.md)
- [messaging](../messaging.md) — sync vs async
- [0072-edge-fleet-rollout](0072-edge-fleet-rollout.md)
- [0063-application-git-lifecycle](0063-application-git-lifecycle.md)
