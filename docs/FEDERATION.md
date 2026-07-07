# Object federation (PF-13 spike)

Spike реализации REQ-PF-13: реестр peer-инстансов, proxy read/write объектов и catalog sync.

Полная vision — [FEDERATION.md](FEDERATION.md), roadmap [ROADMAP.md § Phase 4–8](ROADMAP.md#phase-4--scale--topology-p3).

## Принцип

**Object path ≠ service endpoint.** Путь `root.platform.devices.x` — стабильный идентификатор в каталоге. URL удалённого ISPF хранится в peer registry.

## Компоненты (spike)

| Компонент | Описание |
|-----------|----------|
| `federation_peers` (V26, V29) | Таблица peer: name, baseUrl, authToken, pathPrefix, enabled, auth lifecycle (V29) |
| `federation_inbound_registrations` (V30) | Одноразовые registration codes для inbound tunnel |
| `federation_outbound_agents` (V30) | Конфиг исходящих tunnel-агентов на edge |
| `GET/POST/PUT/DELETE /api/v1/federation/peers` | CRUD (admin) |
| `GET/POST /api/v1/federation/peers/{id}/auth-status`, `refresh-token` | Auth lifecycle для service account peers |
| `POST/GET/DELETE /api/v1/federation/inbound/registrations` | Hub: выпуск registration code |
| `GET /api/v1/federation/tunnels` | Hub: активные tunnel sessions |
| `POST/GET/PUT/DELETE /api/v1/federation/outbound/agents`, `connect` | Edge: CRUD outbound agent |
| `/ws/federation/tunnel` | WebSocket tunnel (registration code или session reconnect) |
| `POST /api/v1/federation/remote-token` | Логин на remote ISPF (server-side), возвращает Bearer для `authToken` |
| `POST /api/v1/security/users/{username}/federation-token` | Выпуск Bearer-сессии для service user на **этом** узле |
| `POST /api/v1/federation/peers/{id}/sync-catalog` | Импорт remote object list в локальное дерево (опц. body: resolutions) |
| `GET /api/v1/federation/peers/{id}/catalog-sync-preview` | Preview: create/update counts + конфликты перед sync |
| Proxy-узлы | AGENT с переменными `federationProxy`, `federationPeerId`, `federationRemotePath` |
| `GET /api/v1/federation/proxy/objects/by-path` | Прямой proxy-read без catalog sync |
| `PATCH /api/v1/federation/proxy/objects/by-path/variables/value` | Proxy-write переменной на remote peer |
| `POST /api/v1/federation/proxy/objects/by-path/functions/invoke` | Proxy-invoke функции на remote peer |
| `GET /api/v1/objects/by-path` | Для proxy-узлов в дереве — прозрачный read через peer |
| `GET /api/v1/dashboards/by-path` | Proxy layout; widget paths remapped на `root.platform.federation.{peer}.*` |
| `PUT /api/v1/dashboards/by-path/layout` | Local или federated: layout проксируется на remote с unremap путей виджетов |
| `PUT /api/v1/dashboards/by-path/title` | Local или federated: title проксируется на remote |
| `GET /api/v1/objects/by-path/variables/history*` | Proxy historian для federated paths |
| `FederationWebSocketFanoutService` | Fan-out platform events к подписчикам federated paths (базовый notify) |
| `POST/PATCH/DELETE /api/v1/federation/binds` | Federation bind: overlay remote peer на **локальный** путь (REQ-PF-13c) |
| `POST /api/v1/federation/binds/probe` | Проверка remote target перед bind |

## Catalog sync vs federation bind (REQ-PF-13c)

| | Catalog sync | Federation bind |
|--|--------------|-----------------|
| Назначение | Bulk-обзор всего каталога edge | Production-структура: объект «свой» по локальному пути |
| Локальный путь | `root.platform.federation.{peer}.*` | Любой путь оператора, напр. `root.platform.devices.edge-pump` |
| Remote | Источник данных через metadata | То же |
| UX | Sync на панели peers | Inspector → **Federation bind**; mirror → **Разместить локально…** |

**Bind** («заражение»): существующий или новый локальный узел получает переменные `federationProxy`, `federationPeerId`, `federationRemotePath`. Read/write/history/dashboard проксируются через `FederationProxyService`. Локальный driver (DEVICE) останавливается при bind; local variables сохраняются, но скрыты до unbind. Перед overlay сохраняется snapshot (`displayName`, `description`, `type`, флаг running driver); **unbind** восстанавливает эти свойства и перезапускает driver, если он работал до bind.

```http
POST /api/v1/federation/binds
{
  "localPath": "root.platform.devices.edge-pump",
  "peerId": "<uuid>",
  "remotePath": "root.platform.devices.demo-sensor-01"
}
```

Create + bind: `parentPath` + `name` вместо `localPath`. Rebind: `PATCH /api/v1/federation/binds`. Unbind: `DELETE /api/v1/federation/binds?localPath=...`.

**Одинаковый путь на remote peer (типичный сценарий):** локальный `root.platform.devices.snmp-localhost` можно привязать к remote peer с тем же `remotePath` — данные берутся с edge, локальный driver и переменные заморожены. Пути совпадают **на разных инстансах**, это нормально.

**Защита от циклов:**

- Запрещено: `localPath` = `remotePath` на **том же** ISPF (loopback peer → бесконечный proxy).
- Запрещено: `remotePath` указывает на **локальный** federation-bound узел (в т.ч. mirror `root.platform.federation.*`), особенно если его remote target уже равен bind `localPath`.
- Один hop: нельзя chain через другой local federated path.

```http
POST /api/v1/federation/binds
{
  "localPath": "root.platform.devices.snmp-localhost",
  "peerId": "<edge-tunnel-peer>",
  "remotePath": "root.platform.devices.snmp-localhost"
}
```

## Catalog sync

### Preview и конфликты (BL-45)

Перед sync Web Console открывает диалог **Catalog sync** (`FederationCatalogSyncDialog`): `GET /api/v1/federation/peers/{peerId}/catalog-sync-preview`.

| Тип конфликта | Когда | Resolution |
|---------------|-------|------------|
| `LOCAL_NATIVE` | На локальном пути уже есть **не-proxy** объект | `SKIP` (по умолчанию) или `BIND` (перезаписать proxy-метаданными) |
| `PROXY_MISMATCH` | Proxy существует, но `peerId` / `remotePath` не совпадают с импортом | `SKIP` или `BIND` |

Применение:

```http
POST /api/v1/federation/peers/{peerId}/sync-catalog
Content-Type: application/json

{
  "resolutions": [
    { "localPath": "root.platform.federation.site-a.devices.pump-01", "action": "BIND" }
  ]
}
```

`action`: `SKIP` | `BIND`. Пустой body — sync без resolutions: конфликты **пропускаются** (безопаснее, чем silent overwrite).

Ответ: `{ localRoot, created, updated, remoteCount, skipped }`.

### Импорт узлов

`POST /api/v1/federation/peers/{peerId}/sync-catalog` загружает список объектов с peer и создаёт локальные proxy-узлы:

```text
root.platform.federation.{peer-name}.devices.demo-sensor-01
  federationPeerId = <uuid>
  federationRemotePath = root.platform.devices.demo-sensor-01
  federationProxy = true
```

Web Console: кнопка **Sync catalog** на панели Federation peers → preview-диалог → **Sync catalog**.

Повторный sync **идемпотентен** для совпадающих proxy: узлы с тем же `peerId` + `remotePath` обновляют metadata (`updated`). При loopback peer пути `root.platform.federation.*` на remote side игнорируются, чтобы не создавать вложенные зеркала каталога.

### Selective subtree sync (BL-119, S22)

Синхронизация **части** remote-каталога вместо полного `sync-catalog`:

| Endpoint | Описание |
|----------|----------|
| `GET /api/v1/federation/peers/{id}/subtree-sync-preview?remoteSubtreePath=…&localParentPath=…` | Preview create/update/conflicts для поддерева |
| `POST /api/v1/federation/peers/{id}/sync-subtree` | Применить sync поддерева |

Body `sync-subtree`:

```json
{
  "remoteSubtreePath": "root.platform.devices",
  "localParentPath": "root.platform.federation.site-a.devices",
  "resolutions": []
}
```

- `remoteSubtreePath` обязателен, должен быть под `pathPrefix` peer (по умолчанию `root.platform`).
- `localParentPath` опционален; по умолчанию зеркало строится под `root.platform.federation.{peer}.*` с тем же suffix.
- Конфликты и resolutions — как у full catalog sync.

**Web Console:**

- Federation peers → **Sync devices subtree** (быстрый preset `root.platform.devices`).
- Explorer → federation mirror folder → Federation tab → **Sync this folder from peer**.

## Recovery runbook + SLO (BL-120, S22-05)

| Сценарий | Симптом | Действие | SLO |
|----------|---------|----------|-----|
| Peer disabled | Proxy 400 «Peer is disabled» | `PUT /peers/{id}` → `enabled: true` | ≤ 5 min до re-enable |
| Peer down (RED) | Health RED, proxy timeout | Проверить URL/tunnel/token; `refresh-token` для service account | ≤ 15 min MTTR (ops) |
| Partial catalog | Нужны только devices | `sync-subtree` вместо full sync | — |
| Store-forward backlog | Tunnel offline, buffered events | Дождаться reconnect или `connect` outbound agent | Replay ≤ 60 s после reconnect (lab) |

**Chaos coverage:** `FederationChaosIntegrationTest` — disable peer → proxy/sync fail → re-enable → sync succeeds; subtree variant (`disabledPeerBlocksSubtreeSyncAndRecoversAfterReEnable`) — `sync-subtree` only imports devices, not dashboards.

### Integration test flake budget (S27)

Tunnel and store-forward ITs poll async connect/replay; budgets live in `FederationIntegrationTestSupport`:

| Constant | Local | CI | Used by |
| -------- | ----- | -- | ------- |
| `TUNNEL_CONNECT_TIMEOUT_SECONDS` | 60 | 120 | `FederationTunnelIntegrationTest`, `FederationStoreForwardIntegrationTest` |
| `PROXY_READY_TIMEOUT_SECONDS` | 30 | 60 | `FederationTunnelIntegrationTest` |
| `BUFFER_DRAIN_TIMEOUT_SECONDS` | 90 | 120 | `FederationStoreForwardIntegrationTest` |
| `CONNECT_RETRY_INTERVAL_MS` | 5000 | 5000 | tunnel connect retry |

Tests marked `@Isolated` (`FederationChaosIntegrationTest`, `FederationTunnelIntegrationTest`, `FederationStoreForwardIntegrationTest`) — no parallel execution with other IT classes. Nightly gate: [ci-nightly.yml](../.github/workflows/ci-nightly.yml) job **Federation integration gate (S27)**. On timeout failure, triage per [CI_FLAKY_TRIAGE.md](CI_FLAKY_TRIAGE.md) (P1 if &lt;1×/week).

**Ops checklist:**

1. `GET /api/v1/federation/peers` — health badge GREEN/YELLOW.
2. `GET /api/v1/federation/peers/{id}/health` — `lastProxyError`, latency.
3. При деградации — disable peer (fail-fast), fix upstream, re-enable, `sync-subtree` или `sync-catalog`.
4. Tunnel peers — `GET /api/v1/federation/tunnels`, outbound buffer stats.

## Dashboard write через proxy (BL-46)

Federated dashboard (`federationProxy=true` под `root.platform.federation.*` или bind на `DASHBOARD`) раньше возвращал **403** на `PUT layout/title`. Теперь:

1. Клиент сохраняет layout с **локальными** путями виджетов (`root.platform.federation.{peer}.*`).
2. `DashboardController` → `FederationProxyService.proxyDashboardSaveLayout` / `SaveTitle`.
3. `FederationPathRemapper.unremapLayoutJson` переводит пути обратно в remote prefix peer.
4. `PUT` на remote `/api/v1/dashboards/by-path/layout|title`.
5. Ответ снова remap'ится для локального UI.

`refreshIntervalMs` по-прежнему через variable PUT на proxy-объекте (как и другие writable variables).

## pathPrefix

Если peer обслуживает ту же иерархию, но консоль передаёт относительный путь:

- `path=devices.demo-sensor-01` + `pathPrefix=root.platform` → remote `root.platform.devices.demo-sensor-01`

## Auth между инстансами

В peer сохраняется `authToken` (Bearer service account на удалённом ISPF). Токен не возвращается в list API (`hasAuthToken: true/false`).

Если `authToken` не задан, исходящие запросы к peer используют Bearer-токен текущего пользователя (удобно для loopback на `127.0.0.1` с включённым RBAC). Для фоновых задач без HTTP-контекста token на peer обязателен.

### Web Console

Панель **Federation peers** (Explorer → Platform → Federation):

1. **Токен для federation (этот узел)** — выбор пользователя и TTL, кнопка «Выпустить токен». Токен копируется в peer на другом ISPF.
2. При создании peer:
   - **Текущий сессионный токен** — подставляет Bearer вашей сессии (loopback).
   - **Получить токен с remote** — `POST /api/v1/federation/remote-token` с `baseUrl` из формы и учётными данными remote (пароль не сохраняется на сервере).

Токены — обычные platform session tokens (TTL по умолчанию 12 ч, макс. 168 ч), не долгоживущие API keys. Нужен профиль `local` или `ispf.security.token-auth-enabled: true`.

### Auth lifecycle (Phase 7.1)

Peer может использовать `authMode`:

| Режим | Описание |
|-------|----------|
| `STATIC_TOKEN` | Bearer вручную (по умолчанию) |
| `SERVICE_ACCOUNT` | Логин/пароль на remote; пароль шифруется (`ispf.security.secrets-key`) |

Для `SERVICE_ACCOUNT`:

- `@Scheduled` refresh за ~20% TTL (мин. 1 ч) до expiry
- При 401 от peer — один immediate retry после refresh
- `GET /api/v1/federation/peers/{id}/auth-status` — диагностика
- `POST /api/v1/federation/peers/{id}/refresh-token` — ручной refresh

Рекомендуется dedicated user `federation-agent` с role operator/admin и tenant scope.

### Outbound tunnel (NAT edge, Phase 7.2)

Local edge за NAT **не доступен inbound**. Edge инициирует исходящий WebSocket к public hub:

```text
Edge (NAT) ──WS outbound──► Hub (public)
Hub ──proxy_request──► Edge ──local services──► response
```

**Operator flow:**

1. **Hub:** Federation → Inbound registration → имя + pathPrefix → **registration code** (один раз).
2. **Edge:** Federation → Outbound agent → hub URL + code → Connect.
3. Hub auto-creates peer `connection_mode=TUNNEL_INBOUND`.
4. **Sync catalog** на hub → `root.platform.federation.{site}.*`.
5. Read/write/invoke работают через tunnel; `event_notify` заменяет HTTP poll для WS subscribe.

Требования prod: `ispf.security.secrets-key` на edge (хранение registration code / session token); reverse proxy с поддержкой `wss`.

### Store-and-forward (BL-117)

При разрыве исходящего tunnel edge **не теряет** platform events, которые hub должен получить через `event_notify`:

1. `FederationOutboundEventBufferRegistry` держит per-agent in-memory буфер.
2. Пока WS disconnected, `FederationTunnelAgentService` кладёт `ObjectChangeEvent` в буфер вместо отправки.
3. После reconnect — `replayBufferedEvents()` с ordering guard на hub (`lastEventSeqByPeer`).

Конфиг (`application.yml`):

```yaml
ispf.federation:
  outbound-buffer:
    max-bytes: 2097152          # ISPF_FEDERATION_OUTBOUND_BUFFER_MAX_BYTES
    drop-policy: DROP_OLDEST    # или DROP_NEWEST
  health:
    stale-minutes: 15           # ISPF_FEDERATION_HEALTH_STALE_MINUTES
```

| Политика | Поведение при переполнении |
|----------|----------------------------|
| `DROP_OLDEST` | Удаляет старейшие события, принимает новое |
| `DROP_NEWEST` | Отклоняет новое событие, сохраняет очередь |

`event_notify` включает опциональные `seq` и `occurredAt` для детерминированного replay.

Тесты: `FederationOutboundEventBufferTest`, `FederationStoreForwardIntegrationTest`.

**Limits (v1):** буфер только in-memory — при restart edge/hub события в очереди теряются. Не покрывает HTTP-only peers без outbound tunnel.

### Peer health SLO (BL-118)

Per-peer диагностика для оператора и алертинга:

| Сигнал | Источник |
|--------|----------|
| Tunnel connected | `FederationTunnelHubService` |
| Auth status | `FederationPeer.authStatus` |
| Last proxy success / latency / error | `FederationPeerHealthService` (HTTP + tunnel proxy) |
| Pending buffered events | outbound buffer registry |

**Limits (v1):** proxy snapshots and buffer stats are in-memory only (reset on server restart).

**Automated alerting (S27):** `FederationPeerHealthMonitor` fires `peerHealthDegraded` / `peerHealthRecovered` on `root.platform.federation` when an enabled peer transitions RED ↔ GREEN. Configure alert rules on those events for webhook/email.

**API:**

```http
GET /api/v1/federation/peers/{id}/health
```

Ответ: `{ peerId, level, tunnelConnected, lastProxySuccessAt, lastProxyLatencyMs, lastProxyError, pendingBufferedEvents, summary }`.

`level`: `GREEN` | `YELLOW` | `RED` — stale proxy &gt; N мин, tunnel offline, auth failed, pending buffer.

List peers (`GET /api/v1/federation/peers`) дополнительно возвращает `healthLevel` и `healthSummary`.

Web Console: колонка health badge на панели **Federation peers** (green/yellow/red).

## Manager-of-managers — federation hub operator shell (BL-188)

**Manager-of-managers (MoM)** is a central **hub** ISPF instance that aggregates many edge sites for operators without logging into each site separately. Edge nodes run behind NAT; hub runs the operator HMI over federated proxy paths.

### Topology

```text
                    ┌─────────────────────────────────────┐
                    │  Hub ISPF (public)                  │
                    │  Operator HMI: ?mode=operator&app=  │
                    │    federation-hub                   │
                    │  root.platform.federation.{site}.*  │
                    └──────────────┬──────────────────────┘
           tunnel WS              │              tunnel WS
     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
     │ Edge site A  │    │ Edge site B  │    │ Edge site C  │
     │ (ARM / NAT)  │    │ (ARM / NAT)  │    │ (ARM / NAT)  │
     └──────────────┘    └──────────────┘    └──────────────┘
```

Deploy profiles: hub uses standard VPS stack; edges use [docker-compose.edge-arm.yml](../deploy/docker-compose.edge-arm.yml) with outbound tunnel enabled.

### Hub setup checklist

1. **Inbound registration** — Federation → Inbound registration → issue registration code per edge site.
2. **Edge connect** — Each edge: Federation → Outbound agent → hub URL + code → Connect.
3. **Peer health** — `GET /api/v1/federation/peers` — all enabled peers GREEN before operator rollout.
4. **Catalog sync** — Full sync or `sync-subtree` for `root.platform.devices` per site (BL-119).
5. **Operator app** — Create `federation-hub` operator app manifest:
   - Dashboards reference `root.platform.federation.{site}.*` paths (remapped widgets).
   - `alarmBar` enabled — federated event fan-out via `FederationWebSocketFanoutService`.
   - Reports optional per-site BFF (invoke via proxy when configured).

### Operator shell UX

| Element | Hub behavior |
|---------|--------------|
| Dashboard tabs | One tab per site or per production area (`site-a-overview`, `site-b-overview`) |
| object-table | Rows from federated device subtree; selectionKey drills into site detail dashboard |
| Live values | Proxy read + WS fan-out; stale badge when peer YELLOW/RED |
| Event journal | Aggregated WARNING+ from all federated prefixes |
| Agent copilot | Scoped to hub operator app prefixes only ([OPERATOR_GUIDE.md](OPERATOR_GUIDE.md)) |

Operators **never** edit federation peers or binds — admin console only.

### Federation bind vs catalog mirror (MoM)

| Pattern | Use in MoM |
|---------|------------|
| **Catalog sync / subtree** | Default — bulk mirror under `root.platform.federation.{peer}.*` |
| **Federation bind** | When hub must show remote device at a **canonical local path** (e.g. same path as edge for training docs) |

Prefer catalog mirror for MoM scale; use bind for ≤10 critical assets that need fixed paths in shared dashboards.

### Health and SLO

Follow [Recovery runbook + SLO (BL-120)](#recovery-runbook--slo-bl-120-s22-05):

- Disable peer on prolonged RED → operator sees last-known values + banner.
- Re-enable after fix → `sync-subtree` if catalog drift suspected.
- Alert on `peerHealthDegraded` events ([Peer health SLO (BL-118)](#peer-health-slo-bl-118)).

### Limits (v1)

- No cross-peer computed bindings on hub (aggregate in hub CUSTOM hub manually if needed).
- Dashboard write proxies to remote — hub operator layout edits affect **remote** dashboard (BL-46).
- MoM operator app is read-only for config; agent copilot is read-only ([BL-179](ROADMAP_PHASE25.md)).

See also: [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md), [deploy/docker-compose.edge-arm.yml](../deploy/docker-compose.edge-arm.yml), [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md) BL-188.

## Ограничения spike / production gaps

- Write proxy — variable patch, function invoke, **dashboard layout/title**; полная двусторонняя синхронизация дерева не поддерживается.
- Catalog sync — import + operator resolutions; без `BIND` конфликтующие local native / proxy mismatch **не перезаписываются**.
- ~~Edge store-forward~~ — **Done (BL-117, S17):** in-memory buffer + replay; disk persistence — backlog.
- ~~Peer health SLO~~ — **Done (BL-118, S17):** health API + UI badges; **alerting (S27):** `peerHealthDegraded` / `peerHealthRecovered` events.
- ~~Federated dashboards read-only~~ — **Done (BL-46):** layout/title write проксируется на remote.
- ~~Catalog sync без merge конфликтов~~ — **Done (BL-45):** preview + SKIP/BIND в UI.
- ~~Tenant scope на federation API~~ — **Done v0.3.0** (`FederationAccessService`, peer CRUD admin-only, proxy scoped).
- ~~WS subscribe-by-path~~ — **Done v0.3.0** (`ObjectWebSocketHandler` + `FederationSubscribePollService` + Web Console hook).

## Пример

```http
POST /api/v1/federation/peers
{ "name": "site-a", "baseUrl": "https://ispf-site-a.example", "pathPrefix": "root.platform" }

GET /api/v1/federation/proxy/objects/by-path?peerId=<uuid>&path=devices.demo-sensor-01
Authorization: Bearer <admin-token>

PATCH /api/v1/federation/proxy/objects/by-path/variables/value?peerId=<uuid>&path=devices.demo-sensor-01&name=temperature
Authorization: Bearer <admin-token>
{ "schema": { "name": "temperature", "fields": [{"name": "value", "type": "DOUBLE"}] }, "rows": [{"value": 22.5}] }
```
