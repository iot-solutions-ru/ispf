> **Язык:** русская версия. Английский оригинал: [en/adr-tension-resolution.md](../en/adr-tension-resolution.md).

# Сводка архитектурных компромиссов и границ ответственности (ADR)

> **Статус:** Charter — как в ISPF разрешены противоречия между удобством быстрой отладки и жёсткими требованиями промышленной эксплуатации. Метки зрелости: [doc-status.md](doc-status.md).

Эта таблица сводит воедино ключевые архитектурные решения платформы, где требовалось согласовать удобство разработчика и безопасность промышленного контура:

| Архитектурный вопрос | Принятое решение в ISPF | Базовый документ |
|----------------------|-------------------------|------------------|
| **Размещение логики на узлах `DEVICE` (`LOGIC_HOST_DEVICE`)** | При интерактивной отладке выдаётся предупреждение (`WARNING`), а при строгой проверке поставки (`ispf validate --strict`, `deploy?strict=true`, каталог решений) — блокирующая ошибка (`ERROR`) | [ADR-0060](decisions/0060-solution-authoring-constraints.md), [ADR-0051](decisions/0051-poka-yoke-constraints-over-guards.md) |
| **Стендовая зрелость драйверов (`PRODUCTION`) против полевой (`FIELD`)** | Стендовые автотесты дают статус `PRODUCTION`, а статус `FIELD` присваивается только после приёмки на реальном заводе по чеклисту C1 | [Уровни зрелости драйверов](driver-promotion.md), [Чеклист C1](../en/field-c1-evidence-checklist.md) |
| **Ручная настройка десятков пулов потоков против готовых профилей** | Введены четыре понятных профиля (`production`, `throughput`, `demo-idle`, `edge`) и метод проверки `GET /api/v1/platform/effective-config` | [ADR-0033](decisions/0033-prod-idle-demostand-tuning.md), [Профили развёртывания](demostands.md) |
| **Поведение кластера при недоступности NATS или Redis** | При включённом кластере (`ISPF_CLUSTER_ENABLED=true`) сервер не стартует в полурабочем состоянии без обязательных шин синхронизации (`ClusterDependencyValidator`) | [ADR-0069](decisions/0069-cluster-consistency.md), [Кластер ISPF](cluster.md) |
| **Безопасность внешних React-приложений (`ui-pack`) на одном домене** | Для маршрутов `/apps/<appId>/` действуют строгие заголовки CSP и токены с ограниченной областью действия (`uiPackAppId`), не дающие доступа к административным API платформы | [ADR-0073](decisions/0073-hosted-ui-pack-trust.md), [ADR-0054](decisions/0054-hosted-ui-packs.md) |
| **Хранение и ротация ключей шифрования секретов** | Секреты хранятся в версионированном криптоконверте `ispf1.<keyId>....` с поддержкой планового перешифрования; внешние аппаратные KMS/HSM не требуются в базовой поставке | [ADR-0065](decisions/0065-secrets-key-rotation.md), [ADR-0076](decisions/0076-external-secrets-providers.md) |

Полный текст на английском языке: **[en/adr-tension-resolution.md](../en/adr-tension-resolution.md)**.
