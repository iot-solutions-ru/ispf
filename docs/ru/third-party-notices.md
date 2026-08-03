> **Язык:** русская версия. Канонический английский: [en/third-party-notices.md](../en/third-party-notices.md).

# Уведомления о стороннем ПО (Уведомления третьих лиц)

Дистрибутив ISPF включает дополнительные компоненты с лицензиями, отличными от кода ISPF. При распространении исходных или двоичных сборок сохраните [LICENSE](../../LICENSE), [NOTICE](../../NOTICE), этот файл и License/Notice-файлы индивидуальных зависимостей.

Настоящий документ является инженерной инвентаризацией, а не юридическим заключением. Для публичного релиза или коммерческой поставки сформируйте SBOM и проведите юридическую проверку.

## Политика лицензирования ISPF

Код платформы в репозитории лицензируется под **GNU AGPL-3.0** ([0016-agpl-dual-licensing](decisions/0016-agpl-dual-licensing.md)). Сторонние библиотеки сохраняют свои лицензии.

Текущее состояние поставки:

- **Платформа (`ispf-server`, `web-console`, core packages):** AGPL-3.0; Enterprise — optional `platform-license.json`.
- **Драйверы устройств:** только **пакеты драйверов** в `${ISPF_DRIVER_PACKS_DIR}`; у каждого пакета свой `LICENSE` и `licenseType`.
- **Протокольные стеки, ранее GPL/коммерческие** (BACnet, DLMS, IEC-104, DNP3, IPMI, RADIUS, M-Bus): заменены **собственными clean-room кодеками ISPF** (лицензия пакета Apache-2.0). На classpath нет bacnet4j / Gurux / j60870 / StepFunc / vxIPMI / TinyRadius / jMBus.
- **Электронная таблица веб-консоли:** встроенный движок `ispfSheetEval`; HyperFormula **не используется**.
- **Шаблоны отчётов:** только Apache POI Band1 (ADR-0053). Путь YARG / JasperReports / docx4j **удалён**.
- **Пакеты приложений:** декларативный JSON + EULA клиента.

## Реестр лицензионных рисков

| Компонент | Где используется | Лицензия | Риск / действие |
|-----------|------------------|----------|-----------------|
| `bacnet4j` / Gurux / j60870 / StepFunc / vxIPMI / TinyRadius / jMBus | *(удалены)* | — | Заменены на `com.ispf.driver.*.codec` (2026-08). |
| YARG / JasperReports / docx4j (отчёты) | *(удалены)* | — | Только Apache POI. |
| `javax.sip:jain-sip-ri` | `ispf-driver-sip` | Public Domain (NIST) | `LicenseRef-NIST-PublicDomain`. |
| `jSerialComm` | `ispf-driver-modem-at` | Apache-2.0 / LGPL-3.0 dual | В SBOM указывать **Apache-2.0**. |
| UnboundID LDAP SDK | `ispf-driver-ldap` | Apache / GPL / LGPL / Free Use | Для поставки — **Apache / Free Use**. |

## Рекомендация по лицензионной модели

1. **Платформа** — AGPL-3.0; сетевое использование → source-offer, если нет Enterprise EULA.
2. **Драйверы** — runtime только в пакетах; кодеки ISPF под Apache-2.0 (кроме SIP NIST).
3. **Коммерческая поставка:** Enterprise platform license + отдельные EULA для application bundles.

## Генерация SBOM

```bash
node tools/license-audit/check-all.mjs
node tools/license-audit/generate-sbom.mjs
```

Подробная инвентаризация Java/npm: [en/third-party-notices.md](../en/third-party-notices.md).
