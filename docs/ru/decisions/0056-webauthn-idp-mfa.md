> **Язык:** русская версия. Канонический английский: [en/decisions/0056-webauthn-idp-mfa.md](../../en/decisions/0056-webauthn-idp-mfa.md).

# ADR-0056: WebAuthn / OTP IdP как продолжение MFA (BL-194)

## Статус

**Proposed** (2026-09-05) — намерение продукта зафиксировано; реализация **не** начата. Родитель: BL-153 TOTP GA **Done**; строка бэклога **BL-194 Planned**.

## Контекст

В корпоративных тендерах всё чаще требуют MFA, устойчивый к фишингу (passkeys / WebAuthn), или OTP, принудительный на стороне IdP (Keycloak / Entra). ISPF уже поставляет **сохраняемый TOTP** с необязательным `required-for-admin` (BL-153). Дальнейшее наращивание TOTP дублировало бы возможности IdP и откладывало passkeys.

## Решение

1. **Оставить TOTP** всегда доступным локальным путём MFA (air-gap / без IdP).
2. **BL-194 даёт два дополняющих пути** (любой может выйти первым, если блокирует именной тендер):
   - **WebAuthn / passkeys** для локальных пользователей и, по выбору, для пользователей, привязанных к OIDC: ceremony регистрации, assertion `publicKey` при входе, документированная политика восстанавливаемого backup.
   - **OTP IdP / WebAuthn через Keycloak (или совместимый OIDC IdP)** — **предпочтительный** MFA, когда ISPF настроен на OIDC. Платформа не реализует UX OTP заново: она требует claims `acr`/`amr`, согласованные в настройках realm.
3. **MFA по умолчанию выключен**, пока администратор не включит TOTP и/или политики IdP — та же честность, что и сейчас.
4. Организацию нельзя называть «только passwordless», пока восстановление WebAuthn и fallback IdP не описаны в [security.md](../security.md).

### Non-goals (этот ADR)

- Замена RBAC / per-variable ACL (BL-154 Done).
- Обязательный hardware security module.
- Само по себе закрытие G-01 pen-test.

## Последствия

- Ясное разделение: air-gap TOTP и enterprise IdP / passkeys.
- В тендере можно ссылаться на BL-194, не выдавая TOTP за WebAuthn.

Риски:

- Новые ceremony в браузере и выбор политики attestation.
- Матрица тестов: Chrome/Safari/Android; восстановление, если устройство потеряно.
- Консоль, API и документация EN/RU; пересчёт Security в scorecard только после evidence.

## Набросок реализации (после снятия с парковки)

| Срез | Поставка |
|------|----------|
| A | Флаги `ispf.security.webauthn.*`; конфигурация Relying Party id/origins |
| B | API enrollment и login; credential ids хранятся в виде хеша; audit events |
| C | Web Console: регистрация и assert passkey; TOTP остаётся параллельным путём |
| D | Документация OIDC: OTP/WebAuthn Keycloak как основной MFA; проверки claims в ISPF |
| E | Evidence в `docs/evidence/security-webauthn/` и обновление G-04 |

**Триггер снятия с парковки:** именной тендер или требование заказчика к MFA (см. [parked-backlog](../parked-backlog.md) **P-WEBAUTHN**, когда строка появится).

## Ссылки

- [security.md](../security.md) — раздел MFA
- [compliance-tender-pack.md](../compliance-tender-pack.md) — G-04
- [pen-test-scope.md](../pen-test-scope.md) — подготовка G-01
- Roadmap BL-194
