> **Язык:** краткая русская версия. Канон (полный маппинг + gap register): [en/compliance-tender-pack.md](../en/compliance-tender-pack.md).

# Compliance tender pack (БЛ-192)

> **Статус:** Stable — документационный пакет для enterprise-тендеров. Теги: [doc-status](../en/doc-status.md).

Честное описание **уже существующей** безопасности ISPF для тендерных приложений (IEC 62443-lite + GAMP-lite). Это **не** сертификат IEC 62443, не GAMP/CSV-пакет и не отчёт о pen-test.

| | |
| --- | --- |
| **Канон** | [en/compliance-tender-pack.md](../en/compliance-tender-pack.md) |
| **Backlog** | [БЛ-192](roadmap.md#бл-191193--аудит-доменов-follow-up) — **Готово** (docs pack) |
| **Связанные** | [security](security.md), [multi-tenant](multi-tenant.md), [collaboration](collaboration.md), [license-compliance](license-compliance.md), [certification](certification.md), [competitive-scorecard](competitive-scorecard.md) |

---

## Что есть / чего нет (кратко)

| Тема | Поза | Класс |
| ---- | ---- | ----- |
| RBAC + object/variable ACL | HTTP-роли, ACL, `readRoles`/`writeRoles` | **Exists** / ACL-шаблоны **Partial** |
| MFA (TOTP) | Enrollment + опция `required-for-admin` | **Exists** (BL-153 Готово); WebAuthn → **BL-194** |
| Multi-tenant | Logical SaaS A≠B + `tenant-admin`; OIDC claim; hard schema hooks | **Exists** (logical SaaS); hard table routing — **Partial** |
| Audit | Security audit + CSV + SIEM webhook; config audit; AI audit | **Exists** (BL-156 Done); WORM/GxP — ops |
| Pen-test / 62443 cert / GAMP IQ-OQ | — | **Gap** — не заявлять |

Полные таблицы IEC 62443-lite, GAMP-lite и реестр пробелов (G-01…G-08) — только в [английской канонической странице](../en/compliance-tender-pack.md).

---

## Как отвечать в тендере

1. Приложить EN-канон (или PDF) как annex «compliance posture».
2. Открытые строки gap register копировать как **Partial / Gap**, не как Done.
3. Отделять функции платформы от **валидации площадки** (IQ/OQ/PQ) и от [лицензионных обязательств](license-compliance.md).
