# Безопасность и RBAC

## Модель

ISPF использует **ролевой доступ** на уровне HTTP API:

| Роль | Spring authority |
|------|------------------|
| `admin` | `ROLE_admin` |
| `developer` | `ROLE_developer` |
| `operator` | `ROLE_operator` |

Per-object ACL — см. [SECURITY.md](SECURITY.md) (`object_acl_entries`, вкладка «Доступ» в Web Console).

## Профили аутентификации

### local

Файл: `application-local.yml`

- OAuth отключён (dummy issuer)
- RBAC включён; аутентификация по **Bearer-токену** после `POST /api/v1/auth/login`
- `ispf.security.token-auth-enabled: true`
- `ispf.security.local-default-role:` пусто — без токена доступ запрещён
- `LocalBearerTokenFilter` + опциональный fallback `X-ISPF-Role` (только dev)

Учётные записи по умолчанию (если БД пуста): `admin/admin`, `developer/developer`, `operator/operator`.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/objects
```

Web Console: экран входа; сессия хранится в `localStorage`. В профиле `dev`/prod — **OIDC Authorization Code + PKCE** через Keycloak (кнопка «Войти через Keycloak»). Конфигурация: `GET /api/v1/auth/config`. Админ управляет пользователями в дереве `root.platform.security.users`.

**Автозапуск приложения:** у пользователя можно включить `autoStartEnabled` и указать `autoStartApp` (id operator app, список — `GET /api/v1/operator-apps`). После входа Web Console открывает operator-приложение вместо админ-консоли.

### Управление пользователями (admin)

| Endpoint | Описание |
|----------|----------|
| `GET /api/v1/security/users` | Список пользователей |
| `POST /api/v1/security/users` | Создать пользователя |
| `PUT /api/v1/security/users/{username}` | Обновить (роли, enabled, displayName, **autoStartEnabled**, **autoStartApp**) |
| `DELETE /api/v1/security/users/{username}` | Удалить |
| `POST /api/v1/security/users/{username}/password` | Сменить пароль |
| `POST /api/v1/auth/logout` | Завершить сессию |
| `GET /api/v1/auth/me` | Текущий пользователь (с токеном) |
| `GET /api/v1/auth/config` | Режим auth (`local` / `oidc`) для Web Console |

Пользователи и роли синхронизируются в дерево объектов (см. [OBJECT_MODEL.md](OBJECT_MODEL.md)).

### dev / default (production-like)

- OAuth2 Resource Server, JWT от Keycloak
- Issuer: `http://localhost:8180/realms/ispf`
- Роли из JWT: `realm_access.roles` → `ROLE_admin`, `ROLE_operator`

### test

- RBAC отключён (`rbac-enabled: false`)
- Все endpoints доступны без auth

## Матрица доступа

Правила: `IspfAuthorizationRules.java`.

| Endpoint | admin | developer | operator | public |
|----------|:-----:|:---------:|:--------:|:------:|
| `GET /api/v1/info` | ✓ | ✓ | ✓ | ✓ |
| `POST /api/v1/auth/login` | | | | ✓ |
| `GET /api/v1/auth/me` | ✓ | ✓ | ✓ | |
| `GET /actuator/health` | ✓ | ✓ | ✓ | ✓ |
| `WS /ws/**` | ✓ | ✓ | ✓ | ✓ |
| `GET /api/v1/**` | ✓ | ✓ | ✓ | |
| `POST /api/v1/events/**` | ✓ | ✓ | ✓ | |
| `POST .../functions/invoke` | ✓ | ✓ | ✓ | |
| `POST /api/v1/bff/**` | ✓ | ✓ | ✓ | |
| `POST /api/v1/workflows/instances/*/cancel` | ✓ | ✓ | ✓ | |
| `GET/POST /api/v1/work-queue/**` | ✓ | ✓ | ✓ | |
| `POST/PUT/PATCH/DELETE` конфигурация решений (`/objects`, `/applications`, data-sources, …) | ✓ | ✓ | | |
| `PUT /api/v1/objects/by-path/acl` | ✓ | | | |
| `/api/v1/platform/metrics`, `/runtime-settings`, `/update/**`, … | ✓ | | | |
| `/api/v1/security/**`, `/federation/**`, `/tenants/**` | ✓ | | | |
| `/api/v1/ai/**` (кроме `GET /ai/provider`) | ✓ | ✓ | | |
| `/api/v1/applications/**` deploy | ✓ | ✓ | | |
| `/api/v1/schedules/**` | ✓ | ✓ | | |
| `/api/v1/alert-rules/**`, `/correlators/**` | ✓ | ✓ | | |
| `/api/v1/blueprints/**` (write) | ✓ | ✓ | | |

**operator** может: читать объекты/дашборды/workflows, вызывать функции (кроме script-only вроде `executeQuery`), work queue, fire events, писать значения writable-переменных.

**developer** может: всё для конфигурации решений (объекты, приложения, SQL platform tools, `executeQuery`, **AI Studio**), но **не** системные настройки, security, federation, tenants, license/cluster.

**admin** может: всё выше + платформенное администрирование.

## Keycloak (dev)

Docker Compose поднимает Keycloak на порту **8180**.

Настройка realm `ispf`:

1. Admin console: http://localhost:8180 — user `admin` / `admin`
2. Create realm **ispf**
3. Create client (public или confidential) для web/API
4. Realm roles: `admin`, `operator`
5. Назначить роли пользователям
6. Запуск сервера: `--spring.profiles.active=dev`

Web Console (профиль `dev`): кнопка **Войти через Keycloak** (OIDC PKCE, client `ispf-web-console`). Realm импортируется из `deploy/keycloak/ispf-realm.json` при `docker compose up`.

### Per-object ACL

| Endpoint | Описание |
|----------|----------|
| `GET /api/v1/objects/by-path/acl?path=` | Список правил ACL объекта |
| `PUT /api/v1/objects/by-path/acl?path=` | Заменить правила ACL |

Правила: `principalType` (`ROLE`/`USER`), `principalId`, `permission` (`READ`/`WRITE`/`INVOKE`). Если на объекте или предке нет правил — действует глобальный RBAC. `admin` всегда имеет полный доступ.

Web Console: вкладка **Доступ** в инспекторе объекта (admin).

## Переменные

| Переменная | Описание |
|------------|----------|
| `ISPF_OAUTH_ISSUER` | JWT issuer URI |
| `ispf.security.rbac-enabled` | Вкл/выкл RBAC |
| `ispf.security.token-auth-enabled` | Bearer-сессии (local) |
| `ispf.security.local-default-role` | Роль по умолчанию без токена (local, dev only) |
| `ispf.security.mfa.enabled` | Включить TOTP enrollment API (`/api/v1/security/mfa/**`) |

## MFA (TOTP foundation)

Конфигурация: `ispf.security.mfa.enabled` (по умолчанию `false`, env `ISPF_MFA_ENABLED`).

Когда MFA включён, аутентифицированные пользователи (любая роль с доступом на чтение API) могут:

| Endpoint | Описание |
|----------|----------|
| `GET /api/v1/security/mfa/status` | Статус MFA и pending enrollment |
| `POST /api/v1/security/mfa/enroll` | Начать TOTP enrollment (secret + `otpauth://` URI) |
| `POST /api/v1/security/mfa/verify` | Подтвердить 6-значный код (stub — полная TOTP-проверка в следующем спринте) |
| `DELETE /api/v1/security/mfa/enroll` | Отменить pending enrollment |

Реализация — скелет `MfaService` (in-memory pending state). Production: Keycloak OTP/WebAuthn (BL-153) или персистентное хранение enrollment в ISPF.

## Per-variable ACL

На переменной можно задать `readRoles` / `writeRoles` (JSON-массив имён ролей). Пустой список = наследование object ACL.

Проверка в `ObjectAccessService.requireVariableRead/Write` при чтении/записи значений переменных. Web Console: бейджи и редактор ACL в диалоге переменной.

## Рекомендации для production

- Не использовать профиль `local`
- Keycloak или другой IdP с коротким TTL JWT
- TLS на ingress
- Ограничить `permitAll` endpoints
- Secrets через vault, не в compose
