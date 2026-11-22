> **Язык:** русская версия. Английский оригинал: [en/parked-backlog.md](../en/parked-backlog.md).

# Отложенные задачи и условия их запуска в работу

> **Статус:** Charter — перечень направлений, которые сознательно не форсируются без конкретного промышленного заказчика или целевого проекта. Метки зрелости: [doc-status.md](doc-status.md).

В соответствии с правилом **«стабильность важнее новых функций»** перечисленные ниже задачи остаются в отложенном состоянии до появления конкретного проектного требования на объекте заказчика:

| Код | Направление | Текущее состояние | Условие возобновления работ |
|-----|-------------|-------------------|-----------------------------|
| `P-ENT-L` | Enterprise L (50k history + 1B CH + multi-tag SLO) | **Учтено** в scorecard Historian **9.5** (2026-09-09) | — |
| `P-HMI-8H` | HMI offline **8 h** (lab CDP) | **Учтено** в scorecard HMI **8.0**; для 9+ нужен `P-HMI-FIELD` | — |
| `P-OT` | Полевая сертификация драйверов уровня `FIELD` на заводах | Лабораторные эмуляторы и стенды пройдены (`PRODUCTION`); полевой счётчик `FIELD = 0` | Конкретная площадка заказчика и 7-дневный прогон по [регламенту полевых испытаний](field-pilot-playbook.md) |
| `P-ERP` | Прямой промышленный коннектор к живой базе 1С:ERP / SAP | В пакетах есть транзакционная очередь `outbox` (HTTP / NATS / Kafka) и учебный коннектор | Конкретный проект интеграции с утверждённой схемой документов 1С / SAP |
| `P-HMI-FIELD` | Полевые испытания автономного режима PWA на планшетах в цеху | Автоматические тесты и 8-часовой прогон в лаборатории выполнены | Опытная эксплуатация на планшетах конкретной площадки ([hmi-offline-field-soak.md](hmi-offline-field-soak.md)) |
| `P-BPMN` | Расширение палитры BPMN новыми конструкциями сверх базового ядра ([ADR-0047](decisions/0047-custom-bpmn-subset-engine.md)) | Текущего набора шагов достаточно для технологических регламентов и очередей смены | Подтверждённое требование заказчика и отдельное архитектурное решение (ADR) |
| `P-WEBAUTHN` | Встроенная поддержка аппаратных ключей FIDO2 / WebAuthn внутри `ispf-server` | **Parked** (2026-11-15): TOTP GA в ядре; WebAuthn / IdP OTP — [ADR-0056](decisions/0056-webauthn-idp-mfa.md), [known-limitations](known-limitations.md) | Прямое требование тендера / именованный MFA-заказчик |
| `P-MYSQL-SKU-RERUN` | Повтор B1 checklist MySQL на каждой named non-Community ячейке (Enterprise, HeatWave, RDS / Aurora MySQL, Azure Database for MySQL) | MySQL — **Prod Yes (B1)** только на self-managed Community Server 8.4.x с MariaDB Connector/J (owner extension, [ADR-0079](decisions/0079-mysql-metadata-certification.md)); этот долг — все остальные ячейки; MariaDB не покрывается | Named ячейка заказчика и хост для [checklist](../en/mysql-sku-promotion-checklist.md) |
| `P-MYSQL-TLS` | Повтор B1 gate MySQL по TLS на соединении `ISPF_DB` (`sslMode=verify-full`, без `allowPublicKeyRetrieval`) | Prod Yes (B1) подписан без него: TLS — обязанность оператора, подписью не сертифицирован; ячейка evidence работала без TLS, её `allowPublicKeyRetrieval=true` — только для lab ([checklist](../en/mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)) | Первый named хост MySQL с сертификатом сервера (рекомендованный повтор на named хосте) |
| `P-MYSQL-PG-SQL` | Проверить остальной JDBC SQL на PG-only конструкции на MySQL; портировать или закрыть fence то, что найдёт проверка | Известный non-claim, полного паритета SQL нет. Найденное при подписи исправлено через `RelationalDialect` и работает на MySQL в CI и в gate: `PlatformJobService` (`(UUID) getObject` на `CHAR(36)`, `NULLS LAST`: отчёты через очередь задач, `GET /api/v1/platform/metrics`) и `ApplicationSqlBindingStore` (`\|\|` в списке триггеров) ([checklist](../en/mysql-sku-promotion-checklist.md#known-gaps-tracked-tech-debt)) | Named развёртывание MySQL, которому нужен непроверенный путь, или любой более широкий claim по MySQL |
| `P-PENTEST` | Внешний аудит защищённости (испытания на проникновение сторонней лабораторией) | Подготовлен полный пакет документации для аудиторов ([pen-test-vendor-pack.md](pen-test-vendor-pack.md)) | Заключение договора с внешней лабораторией по запросу заказчика |
| `P-MSSQL-AA-SOAK` | MSSQL A-A AG failover + dual-JVM chaos journal | **Parked** (Prod Yes подписан; soak residual) | Именованный AG стенд + journal soak | Dialect fence снят 2026-11-22 ([A-A checklist](../en/mssql-aa-sku-promotion-checklist.md)) |
| `P-MSSQL-SKU-RERUN` | Per-SKU checklist re-run (Standard / Azure SQL DB\|MI) | **Parked** (owner-extended Yes) | Именованный licensed / PaaS стенд + gate JSON | Evaluation engine-class extended owner 2026-11-22 |

**Связанные разделы:** [Сравнительная оценка зрелости (`competitive-scorecard.md`)](competitive-scorecard.md) · [Дорожная карта (`roadmap.md`)](roadmap.md).
