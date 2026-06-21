# Object federation (PF-13 spike)

Spike реализации REQ-PF-13: реестр peer-инстансов, proxy read/write объектов и catalog sync.

Полная vision — [PLATFORM_DEVELOPER_BACKLOG.md §9](PLATFORM_DEVELOPER_BACKLOG.md#9-распределённая-архитектура-и-федерация-roadmap-p3).

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
| `POST /api/v1/federation/peers/{id}/sync-catalog` | Импорт remote object list в локальное дерево |
| Proxy-узлы | AGENT с переменными `federationProxy`, `federationPeerId`, `federationRemotePath` |
| `GET /api/v1/federation/proxy/objects/by-path` | Прямой proxy-read без catalog sync |
| `PATCH /api/v1/federation/proxy/objects/by-path/variables/value` | Proxy-write переменной на remote peer |
| `POST /api/v1/federation/proxy/objects/by-path/functions/invoke` | Proxy-invoke функции на remote peer |
| `GET /api/v1/objects/by-path` | Для proxy-узлов в дереве — прозрачный read через peer |
| `GET /api/v1/dashboards/by-path` | Proxy layout; widget paths remapped на `root.platform.federation.{peer}.*` |
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

`POST /api/v1/federation/peers/{peerId}/sync-catalog` загружает список объектов с peer и создаёт локальные proxy-узлы:

```text
root.platform.federation.{peer-name}.devices.demo-sensor-01
  federationPeerId = <uuid>
  federationRemotePath = root.platform.devices.demo-sensor-01
  federationProxy = true
```

Web Console: кнопка **Sync** на панели Federation peers.

Повторный sync **идемпотентен**: уже импортированные узлы обновляют proxy-метаданные (`updated`), новые не дублируются. При loopback peer пути `root.platform.federation.*` на remote side игнорируются, чтобы не создавать вложенные зеркала каталога.

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

## Ограничения spike / production gaps

- Write proxy — variable patch и function invoke; полная двусторонняя синхронизация дерева не поддерживается.
- Catalog sync — только import (не merge конфликтов без оператора).
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
