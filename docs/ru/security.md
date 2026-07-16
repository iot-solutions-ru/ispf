> **Язык:** русская версия (вычитка). Канонический английский: [en/security.md](../en/security.md).

﻿# Безопасность и RBAC

> **Статус:** Stable — RBAC, MFA. Теги: [doc-status](../en/doc-status.md).

## Модель

ISPF использует **ролевой доступ** на уровне HTTP API:

| Роль | Весенняя власть |
|------|------------------|
| `admin` | `ROLE_admin` |
| `developer` | `ROLE_developer` |
| `operator` | `ROLE_operator` |

Пообъектный ACL — см. [security](security.md) (`object_acl_entries`, вкладка «Доступ» в веб-консоли).

## Профили аутентификации

### местный

Файл: `application-local.yml`

- OAuth отключен (фиктивный эмитент)
- RBAC включён; аутентификация по **Bearer-токену** после `POST /api/v1/auth/login`
- `ispf.security.token-auth-enabled: true`
- `ispf.security.local-default-role:` пусто — без токена доступ запрещён
- `LocalBearerTokenFilter` + опциональный резервный вариант `X-ISPF-Role` (только dev, **выключен по умолчанию** — `ispf.security.local-role-header-enabled`)

Учётные записи по умолчанию (если БД пуста): `admin/admin`, `developer/developer`, `operator/operator`.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/objects
```

Веб-консоль: экран входа; сессия хранится в `localStorage`. В профиле `dev`/prod — **Код авторизации OIDC + PKCE** через Keycloak (кнопка «Войти через Keycloak»). Конфигурация: `GET /api/v1/auth/config`. Админ руководят пользователями в дереве `root.platform.security.users`.

**Автозапуск приложения:** у пользователя можно включить `autoStartEnabled` и указать `autoStartApp` (id оператора приложения, список — `GET /api/v1/operator-apps`). После входа в веб-консоль открывается оператор-приложение вместо админ-консоли.

### Управление пользователями (admin)

| Конечная точка | Описание |
|----------|----------|
| `GET /api/v1/security/users` | Список пользователей |
| `POST /api/v1/security/users` | Создать пользователя |
| `PUT /api/v1/security/users/{username}` | Обновить (роли, включено, displayName, **autoStartEnabled**, **autoStartApp**) |
| `DELETE /api/v1/security/users/{username}` | Удалить |
| `POST /api/v1/security/users/{username}/password` | Сменить пароль |
| `POST /api/v1/auth/logout` | Завершить сессию |
| `GET /api/v1/auth/me` | Текущий пользователь (с токеном) |
| `GET /api/v1/auth/config` | Режим auth (`local` / `oidc`) для Web Console |

Пользователи и ролики синхронизируются в дереве объектов (см. [object-model](object-model.md)).

### dev/default (как в рабочей среде)

- Сервер ресурсов OAuth2, JWT от Keycloak
- Issuer: `http://localhost:8180/realms/ispf`
- Роли из JWT: `realm_access.roles` → `ROLE_admin`, `ROLE_operator`

### тест

- RBAC отключён (`rbac-enabled: false`)
- Все конечные точки доступны без авторизации.

## Матрица доступа

Правила: `IspfAuthorizationRules.java`.

| Конечная точка | админ | разработчик | оператор | общественный |
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

**оператор** может: читать объекты/дашборды/рабочие процессы, переменные функции (кроме только для скриптов вроде `executeQuery`), рабочую очередь, запускать события, записывать значения записываемых-переменных.

**разработчик** может: всё для конфигурации решений (объекты, приложения, инструменты платформы SQL, `executeQuery`, **AI Studio**), но **нет** системные настройки, безопасность, федерация, тенанты, лицензия/кластер.

**admin** может: всё выше + платформенное администрирование.

## Keycloak (разработчик)

Docker Compose поднимает Keycloak на порт **8180**.

Настройка realm `ispf`:

1. Консоль администратора: http://localhost:8180 — пользователь `admin` / `admin`
2. Создайте область **ispf**.
3. Создайте клиент (публичный или конфиденциальный) для веб/API.
4. Роли в королевстве: `admin`, `operator`
5. Назначить ролики пользователей
6. Запуск сервера: `--spring.profiles.active=dev`

Веб-консоль (профиль `dev`): кнопка **Войти через Keycloak** (OIDC PKCE, клиент `ispf-web-console`). Царство импортируется с `deploy/keycloak/ispf-realm.json` по `docker compose up`.

### ACL для каждого объекта

| Конечная точка | Описание |
|----------|----------|
| `GET /api/v1/objects/by-path/acl?path=` | Список правил ACL объекта |
| `PUT /api/v1/objects/by-path/acl?path=` | Заменить правила ACL |

Правила: `principalType` (`ROLE`/`USER`), `principalId`, `permission` (`READ`/`WRITE`/`INVOKE`). Если на объекте или предке нет правил — используйте глобальный RBAC. `admin` всегда имеет полный доступ.

Веб-консоль: вкладка **Доступ** в инспекторе объекта (admin).

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

Когда MFA включен, аутентифицированные пользователи (любимая роль с доступом к API чтения) могут:

| Конечная точка | Описание |
|----------|----------|
| `GET /api/v1/security/mfa/status` | Статус MFA и pending enrollment |
| `POST /api/v1/security/mfa/enroll` | Начать TOTP enrollment (secret + `otpauth://` URI) |
| `POST /api/v1/security/mfa/verify` | Подтвердить 6-значный код (заглушка — полная TOTP-проверка в следующем спринте) |
| `DELETE /api/v1/security/mfa/enroll` | Отменить pending enrollment |

Реализация — скелет `MfaService` (в памяти в состоянии ожидания). Производство: Keycloak OTP/WebAuthn (BL-153) или персистентное хранение данных в ISPF.

## ACL для каждой переменной

На переменных можно задать `readRoles` / `writeRoles` (JSON-массив имён ролей). Пустой список = наследование объекта ACL.

Проверка в `ObjectAccessService.requireVariableRead/Write` при чтении/записи историй. Веб-консоль: бейджи и редактор ACL в диалоговом окне переменных.

## Рекомендации для производства

- Предпочтительно `--spring.profiles.active=prod` (см. `application-prod.yml`) или переменные окружения ниже
- Не выставлять профиль `local` на internet-facing хостах
- Keycloak или другой IdP с самым коротким TTL JWT
- TLS на входе
- Ограничить `permitAll` endpoints
- Секреты через хранилище, не в конфиге
- `ISPF_LICENSE_ENFORCE=true` и `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true`
- `ISPF_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` — origin(ы) консоли (по умолчанию localhost; в `local`/`test` остаётся `*`)
- `ispf.security.local-role-header-enabled=false`
- Federation outbound login: `ISPF_FEDERATION_BLOCK_LOOPBACK=true` (prod) и опционально `ISPF_FEDERATION_OUTBOUND_URL_ALLOWLIST`

Учётные записи по умолчанию (`admin`/`admin` и т.п.) допустимы только в **local / test / lab** — это не дефект.

`StartupSecurityGuard` пишет предупреждения при старте, если вне `local`/`test` ослаблены license enforce, signed bundles, WS origins или RBAC.
