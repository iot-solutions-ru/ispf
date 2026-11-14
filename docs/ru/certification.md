> **Язык:** русская версия. Английский оригинал: [en/certification.md](../en/certification.md).

# Пути сертификации (BL-190)

> **Статус:** Stable — учебные треки Solution developer и Platform admin, лабораторные работы и банки вопросов в репозитории. Метки зрелости: [doc-status.md](doc-status.md). Полный LMS / прокторинг — во внешнем Partner Portal.

Учебная программа и заготовки экзаменов по сертификации ISPF. Лаборатории ниже можно пройти на lab/VPS уже сейчас.

Соответствует коммерческим уровням [партнёрской программы](partner-program.md).

---

## Обзор треков

| Трек | Аудитория | Результат |
|------|-----------|-----------|
| **Разработчик решения** | Интеграторы, авторы OEM-приложений | Умеет деплоить производственные пакеты без поддержки команды ядра |
| **Администратор платформы** | ИТ/ОТ, DevOps | Умеет усиливать развёртывание, безопасность, архив историй и наблюдаемость |

Дополнительные треки (оператор, специалист MES) — черновики в конце документа.

---

## Трек разработчика решений

Соответствует уровням партнёра **Associate → Professional** в [партнёрской программе](partner-program.md).

### Уровень 1 — Базовый (~16 ч)

| Модуль | Ссылка | Лаборатория |
|--------|--------|-------------|
| Дерево объектов | [Модель объектов](object-model.md) | Создать `DEVICE` + `list_variables` |
| Бандлы | [Приложения](applications.md) | Deploy `demo-app` |
| Дашборды | [Дашборды](dashboards.md) | Виджеты «значение + график» |
| UI оператора | [Руководство оператора](operator-guide.md) | Настроить приложение оператора |

**Экзамен (заготовка):** задеплоить bundle с одним дашбордом; режим оператора открывается без консоли администратора.

### Уровень 2 — Автоматизация (~24 ч)

| Модуль | Ссылка | Лаборатория |
|--------|--------|-------------|
| Драйверы | [Драйверы](drivers.md) | SNMP или virtual device в статусе `RUNNING` |
| Полевые пилоты | [Field pilot](field-pilot-playbook.md) | Заполнить один OT-чеклист |
| Тревоги | [Автоматизация](automation.md) | `configure_alert` + публикация события |
| Рабочие процессы | [workflows](workflows.md) | Пользовательская задача в рабочей очереди |

**Exam (stub):** Alert → correlator → operator notification path.

### Уровень 3 — Производство (~32 ч.)

| Модуль | Ссылка | Лаборатория |
| ------ | --------- | --- |
| Имитаторы SCADA | [scada](scada.md) | Мимик с живыми привязками |
| Федерация | [federation](federation.md) | Привязать удаленное устройство |
| ИИ-агент | [ai-development](ai-development.md) | Сценарий генератора решений (BL-177) |

**Exam (stub):** Комплексное развёртывание агента — адаптация operator UI без ручного редактирования дерева.

---

## Exam question bank (stub)

Машиночитаемые банки живут под [`examples/certification/`](../../examples/certification/). Формат: JSON с `track`, `level`, `version` и `questions[]` (`id`, `type`, `topic`, `prompt`, `options`, `correctIndex`, `reference`).

| Банковский файл | Трек | Уровень | Вопросы |
| --------- | ----- | ----- | --------- |
| [`solution-developer-l1.json`](../../examples/certification/solution-developer-l1.json) | Разработчик решений | Фонд L1 | 8 |
| [`solution-developer-l2.json`](../../examples/certification/solution-developer-l2.json) | Разработчик решений | L2 Автоматизация | 6 |
| [`platform-admin-core.json`](../../examples/certification/platform-admin-core.json) | Администратор платформы | Ядро | 8 |

**Grading (stub):** Partner Portal / LMS integration оценивает multiple-choice локально; практические labs остаются instructor-verified до Phase 32 GA proctoring.

**Пример импорта:**

```bash
curl -s examples/certification/solution-developer-l1.json | jq '.questions | length'
```

---

## Администраторская дорожка платформы

Сопоставляется с внутренними операционными модулями и инфраструктурными модулями Partner **Expert**.

### Основные модули (~24 ч)

| Модуль | Ссылка | Лаборатория |
| ------ | --------- | --- |
| Безопасность | [security](security.md) | RBAC, регистрация MFA, экспорт аудита |
| Развернуть | [deployment](deployment.md) | VPS прямой или скелет Helm |
| Историк | [historian-tiers](historian-tiers.md) | Горячий ярус + экспорт паркета (BL-163) |
| Наблюдаемость | [observability](observability.md) | Сбор метрик + пакет диагностики |

**Exam (stub):** Hardened single-node deploy + backup/restore drill; ClickHouse verify script green.

### Расширенные модули (~16 ч)

| Модуль | Ссылка | Лаборатория |
| ------ | --------- | --- |
| Кластер | [cluster](cluster.md) | Лаборатория двух реплик |
| Мультитенант | [multi-tenant](multi-tenant.md) | Тест записи изоляции тенанта |
| Центр Федерации | [federation](federation.md) | Хаб с 2+ узлами (BL-188) |

**Exam (stub):** Failover drill; tenant A не может читать переменные tenant B.

---

## Значок и продление (проект)

| Товар | Политика |
| ---- | ------ |
| Срок действия бейджа | 12 месяцев |
| Обновление | Дельта-экзамен или модуль непрерывного образования |
| Отзыв | Критический инцидент безопасности или нарушение лицензии |
| Прокторинг | Будет определено позднее — Этап 32 GA |

---

## Выравнивание регрессии агента

Сертификационные labs питают [agent regression suite](agent-regression.md). Цель: ≥95% scenario pass rate (BL-178) **до** live agent grading на Expert exams — **выполнено** на полном live suite 52/52 @100% (см. [competitive-scorecard](competitive-scorecard.md); nightly — platform mode).

---

## Другие треки (черновик)

| Трек | Аудитория | Ключевой документ |
| ----- | -------- | ------- |
| Оператор | Начальники смен | [operator-guide](operator-guide.md) |
| MES-специалист | Инженеры-технологи | [reference-mes-platform](reference-mes-platform.md) |

---

## Связанный

- [partner-program](partner-program.md) — уровни коммерческого партнера
- [competitive-scorecard](competitive-scorecard.md) — измерение 13 Документация/DX
- [roadmap](roadmap.md) — BL-190
