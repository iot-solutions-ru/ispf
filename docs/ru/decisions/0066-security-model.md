# ADR-0066: Модель безопасности платформы (auth, RBAC, tenancy, ACL, secrets)

## Статус

**Accepted** (2026-10-12) — закрепляет уже shipped security-контракт; runtime не меняет.

Канон: [../../en/decisions/0066-security-model.md](../../en/decisions/0066-security-model.md).

## Контекст

Поведение размазано по [security](../security.md), [multi-tenant](../multi-tenant.md), federation on-behalf-of и ADR [0056](0056-webauthn-idp-mfa.md) / [0065](0065-secrets-key-rotation.md). Нужна **одна** запись решения: что ISPF заявляет сегодня, что optional, что Proposed.

## Решение

### 1. Слои (снаружи → внутрь)

| Слой | Контракт |
|------|----------|
| **Authentication** | Local Bearer или OIDC JWT; `X-ISPF-Role` **выключен** по умолчанию |
| **HTTP RBAC** | `admin` / `developer` / `operator` / `tenant-admin` (+ custom); матрица `IspfAuthorizationRules` |
| **Tenancy** | `root.tenant.{id}.*`; virtual root; global `admin` всегда `tenant_id` NULL |
| **Object ACL** | `object_acl_entries` |
| **Member ACL** | Interactive → `MEMBER` (`readRoles` / `writeRoles` / `invokeRoles`); фон → `SYSTEM` |
| **Federation ceiling** | Channel Bearer ∩ `X-ISPF-On-Behalf-Of-*`; заголовки не эскалируют права |
| **Secrets** | Vault/federation ciphertext ([0065](0065-secrets-key-rotation.md)); не в object-tree variables |

### 2. Жёсткие правила

1. `admin` + non-null `tenant_id` **запрещён**.
2. `tenant-admin` не создаёт tenants, не видит чужие ветки, не выдаёт `admin`, не ходит в platform/local JDBC.
3. Interactive user/agent пути — MEMBER; schedulers/bindings — SYSTEM.
4. On-behalf-of = пересечение с ролями канала.
5. Secrets-key / ротация — [0065](0065-secrets-key-rotation.md); plaintext/master key не в дереве.
6. Prod: `local-role-header-enabled=false`; MFA опционален до включения.

### 3. Честность tenancy

Logical SaaS + virtual root + OIDC claim + PG RLS — **Done**. Hard schema hooks — **Done**, но shared tables + RLS. Physical table routing всех platform tables — **не заявлено**. H2 RLS — **no-op**.

### 4. Вне скоупа

WebAuthn / IdP OTP — [0056](0056-webauthn-idp-mfa.md) (Proposed). Pen-test G-01 — evidence, не смена модели.

## Последствия

Runbooks остаются how-to; этот ADR — **граница продуктовых заявлений** для тендеров и scorecard. Новые security-фичи правят этот ADR или дочерний со ссылкой сюда.

## Связанное

- [security](../security.md), [multi-tenant](../multi-tenant.md), [0065](0065-secrets-key-rotation.md), [0056](0056-webauthn-idp-mfa.md)
