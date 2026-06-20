# Object federation (PF-13 spike)

Spike реализации REQ-PF-13: реестр peer-инстансов и proxy-read объектов.

Полная vision — [PLATFORM_DEVELOPER_BACKLOG.md §9](PLATFORM_DEVELOPER_BACKLOG.md#9-распределённая-архитектура-и-федерация-roadmap-p3).

## Принцип

**Object path ≠ service endpoint.** Путь `root.platform.devices.x` — стабильный идентификатор в каталоге. URL удалённого ISPF хранится в peer registry.

## Компоненты (spike)

| Компонент | Описание |
|-----------|----------|
| `federation_peers` (V26) | Таблица peer: name, baseUrl, authToken, pathPrefix, enabled |
| `GET/POST/PUT/DELETE /api/v1/federation/peers` | CRUD (admin) |
| `POST /api/v1/federation/peers/{id}/sync-catalog` | Импорт remote object list в локальное дерево |
| Proxy-узлы | AGENT с переменными `federationProxy`, `federationPeerId`, `federationRemotePath` |
| `GET /api/v1/federation/proxy/objects/by-path` | Прямой proxy-read без catalog sync |
| `GET /api/v1/objects/by-path` | Для proxy-узлов в дереве — прозрачный read через peer |
| `GET /api/v1/dashboards/by-path` | Proxy layout; widget paths remapped на `root.platform.federation.{peer}.*` |
| `GET /api/v1/objects/by-path/variables/history*` | Proxy historian для federated paths |

## Catalog sync

`POST /api/v1/federation/peers/{peerId}/sync-catalog` загружает список объектов с peer и создаёт локальные proxy-узлы:

```text
root.platform.federation.{peer-name}.devices.demo-sensor-01
  federationPeerId = <uuid>
  federationRemotePath = root.platform.devices.demo-sensor-01
  federationProxy = true
```

Web Console: кнопка **Sync** на панели Federation peers.

## pathPrefix

Если peer обслуживает ту же иерархию, но консоль передаёт относительный путь:

- `path=devices.demo-sensor-01` + `pathPrefix=root.platform` → remote `root.platform.devices.demo-sensor-01`

## Auth между инстансами

В peer сохраняется `authToken` (Bearer service account на удалённом ISPF). Токен не возвращается в list API (`hasAuthToken: true/false`).

Если `authToken` не задан, исходящие запросы к peer используют Bearer-токен текущего пользователя (удобно для loopback на `127.0.0.1` с включённым RBAC). Для фоновых задач без HTTP-контекста token на peer обязателен.

## Ограничения spike

- Read-only proxy (object + variables); write/WebSocket не проксируются.
- Полная двусторонняя синхронизация не поддерживается — только import catalog.

## Пример

```http
POST /api/v1/federation/peers
{ "name": "site-a", "baseUrl": "https://ispf-site-a.example", "pathPrefix": "root.platform" }

GET /api/v1/federation/proxy/objects/by-path?peerId=<uuid>&path=devices.demo-sensor-01
Authorization: Bearer <admin-token>
```
