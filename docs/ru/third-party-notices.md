> **Язык:** русская версия. Английский оригинал: [en/third-party-notices.md](../en/third-party-notices.md).

# Уведомления об используемых открытых компонентах третьих лиц

> **Статус:** Stable — перечень сторонних открытых библиотек в составе дистрибутива ISPF и условия их лицензий. Метки зрелости: [doc-status.md](doc-status.md).

Дистрибутив ISPF включает сторонние библиотеки с открытым исходным кодом, которые распространяются на условиях своих оригинальных лицензий (`Apache-2.0`, `MIT`, `BSD`, `EPL-2.0`). При передаче бинарных или исходных сборок сохраняйте корневые файлы [LICENSE](../../LICENSE), [NOTICE](../../NOTICE) и настоящий перечень.

---

## 1. Архитектурные меры по обеспечению лицензионной чистоты

- **Ядро сервера и веб-консоль:** распространяются под лицензией **GNU AGPL-3.0** (или коммерческой лицензией предприятия [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md)).
- **Собственные кодеки промышленных протоколов (`Apache-2.0`):** драйверы BACnet, DLMS/СПОДЭС, МЭК 60870-5-104, DNP3, IPMI, RADIUS и M-Bus используют собственные реализации протоколов `com.ispf.driver.*.codec` без сторонних GPL-зависимостей.
- **Выбор разрешительной лицензии для библиотек с двойным лицензированием:**
  - `com.fazecast:jSerialComm` (модуль `ispf-driver-modem-at` / `modbus-rtu`) — используется на условиях **Apache-2.0**;
  - `com.unboundid:unboundid-ldapsdk` (модуль `ispf-driver-ldap`) — используется на условиях **Apache-2.0 / Free Use**;
  - `javax.sip:jain-sip-ri` (модуль `ispf-driver-sip`) — общественное достояние NIST (`LicenseRef-NIST-PublicDomain`).

---

## 2. Проверка и обновление полного перечня зависимостей

Полный покомпонентный список всех библиотек Java и пакетов npm с указанием версий и текстов лицензий содержится в файле **[en/third-party-notices.md](../en/third-party-notices.md)** и проверяется автоматически командами:

```bash
node tools/license-audit/check-all.mjs
node tools/license-audit/generate-sbom.mjs
```

**Связанные разделы:** [Лицензия и границы поставки (`license.md`)](license.md) · [Лицензионная чистота (`license-compliance.md`)](license-compliance.md) · [Отчёт проверки SBOM (`sbom-legal-review.md`)](sbom-legal-review.md).
