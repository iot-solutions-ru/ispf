> **Язык:** русская версия. Канонический английский: [en/license-compliance.md](../en/license-compliance.md).

# Соответствие лицензии (инженерный контрольный список)

> **Статус:** Stable — Чеклист обязательств.

Инженерные процедуры для выпусков ISPF. **Не юридическая консультация**.

## Режимы лицензии платформы

| Режим | Когда | Обязательства |
|------|------|-------------|
| **Community (AGPL)** | Нет `platform-license.json` | Сетевое использование → AGPL source-offer для модификаций платформы |
| **Enterprise** | Действительный `platform-license.json` | По [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md) |

## Бинарный дистрибутив

1. LICENSE (AGPL)
2. NOTICE
3. [third-party-notices](third-party-notices.md)
4. CycloneDX SBOM (`node tools/license-audit/generate-sbom.mjs`)
5. [sbom-legal-review](sbom-legal-review.md)
6. На пакет драйвера: `LICENSE`, `THIRD_PARTY-NOTICE.txt`

## Профили пакетов драйверов

| Профиль | Назначение |
|---------|----------|
| `permissive` (**по умолчанию**) | Apache-2.0 + NIST SIP |
| `all` | Тот же набор (GPL/LGPL/MPL/StepFunc packs удалены из каталога) |

Бывшие copyleft-пакеты (BACnet, DLMS, IEC-104, DNP3, IPMI, RADIUS, M-Bus) используют **собственные кодеки ISPF** с `licenseType: Apache-2.0`.

## Отчёты

Шаблоны Band1 — только **Apache POI**. YARG / JasperReports / docx4j удалены из `ispf-server`.

## Предрелизный аудит

```bash
node tools/license-audit/check-all.mjs
node tools/license-audit/generate-sbom.mjs
```

## Связанные документы

- [license](license.md)
- [third-party-notices](third-party-notices.md)
- [sbom-legal-review](sbom-legal-review.md)
- [licensed-driver-packs](licensed-driver-packs.md)
- [commercial-licensing](commercial-licensing.md)
- [fto-object-tree-model](fto-object-tree-model.md) — FTO-memo по классу object-tree / V/F/E / bindings
