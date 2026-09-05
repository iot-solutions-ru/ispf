# Область независимого pen-test (подготовка G-01)

> **Статус:** только подготовка — **не** заявление о пройденном assessment.  
> Реестр пробелов: [compliance-tender-pack.md](compliance-tender-pack.md) **G-01**.

Черновик правил engagement для SOW. Закрывает *prep* G-01; сам G-01 закрывается датированным отчётом третьей стороны.

## Цель

Показать, что ISPF (server + web console + типичные поверхности) выдерживает ограниченный black/grey-box assessment без неснятых Critical/High **или** что findings имеют владельцев и сроки.

**Не цель:** сертификация IEC 62443, пакет GAMP CSV, red-team чужого OT.

## In scope (SOW по умолчанию)

| Поверхность | Заметка |
|-------------|---------|
| HTTP/JSON API `/api/v1/**` | Authn/Z, TOTP MFA, tenancy, audit |
| Web Console | XSS/CSRF, токены, обход ролей |
| WebSocket | Handshake, ACL подписок |
| Historian / CH paths | IDOR; без деструктивной нагрузки без согласия |
| OIDC / local login | Escalation, MFA bypass |
| Upload driver packs | Отказ вредоносных пакетов |
| Multi-tenant isolation | A ↛ B |

## Out of scope

Прод-площадки заказчика, PLC/safety, DoS demostand, social engineering, полный white-box без доп. SOW.

## Правила

Письменный допуск; лимиты RPS; без выноса PII; disclosure Critical ≤24h / High ≤5 раб. дней; stop при safety-риске.

## Deliverables для закрытия G-01

Отчёт + retest; желательно `docs/evidence/security-pentest/` (редакция). Иначе в тендере — **Gap (G-01)**.

EN: [pen-test-scope.md](../en/pen-test-scope.md) · ADR [0056](../en/decisions/0056-webauthn-idp-mfa.md)
