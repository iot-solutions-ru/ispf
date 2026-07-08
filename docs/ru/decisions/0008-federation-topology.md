> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0008-federation-topology.md](../../en/decisions/0008-federation-topology.md).

# ADR-0008: Federation topology

Статус: **Принято**  
Дата: 2026-06-21

## Контекст

Распределённые установки ISPF (hub + edge) требуют единой модели: стабильный object path, proxy read/write, catalog sync и production bind. Spike PF-13 реализован; нужно зафиксировать топологию и границы.

## Решение

1. **Object path ≠ endpoint** — `root.platform.devices.x` — логический идентификатор; URL peer хранится в `federation_peers`, не в path.
2. **Топология hub–spoke** — hub держит реестр peers; edge регистрируется через inbound registration code или outbound tunnel agent (`/ws/federation/tunnel`).
3. **Два режима интеграции:**
   - **Catalog sync** — bulk-импорт remote catalog под `root.platform.federation.{peer}.*` для обзора.
   - **Federation bind** — overlay remote объекта на **локальный** путь оператора (`federationProxy`, `federationPeerId`, `federationRemotePath`); local driver останавливается до unbind.
4. **Sync RPC для операций** — proxy read/write/history/dashboard через HTTP API hub; не замена event bus между инстансами.
5. **Auth** — service account Bearer на peer (`federation-token`, `remote-token`); токены в peer registry с lifecycle refresh (V29).
6. **Вне scope** — auto-merge конфликтующих деревьев, billing/quota federation, единый глобальный namespace без явного bind.

## Последствия

- Реализация: `FederationProxyService`, `FederationPeerService`, tunnel WebSocket — см. [federation.md](../federation.md).
- WebSocket fan-out уведомляет подписчиков federated paths; полная replica consistency — через NATS только внутри одного кластера platform.
- Dashboard widget paths remapped на federated prefix при proxy layout.

## Связанные материалы

- REQ-PF-13 — [roadmap.md](../roadmap.md), [federation.md](../federation.md)
- [messaging.md](../messaging.md) — sync vs async
