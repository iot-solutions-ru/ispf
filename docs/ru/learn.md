> **Язык:** зеркало. Канон: [en/learn.md](../en/learn.md).

# Обучение ISPF — прозрачный вход

> **Статус:** Stable — хаб обучения. Теги: [doc-status.md](doc-status.md).

**Одна страница: где вы сейчас и что открыть дальше.**  
Остальные гайды, лабы и сертификация висят на этой карте.

Каркас: **Getting started → Quick start → Curriculum → How-to → How not to → Troubleshooting**.  
Только текст и практика — без видео.

---

## Ориентация за 60 секунд

| Вопрос | Ответ в ISPF |
|--------|----------------|
| Где живёт решение? | **Дерево объектов** (`root.platform…`) — один runtime |
| Куда класть логику? | Хаб **SINGLETON** или twin **INSTANCE** — никогда `DEVICE` |
| Как отгружать? | **Bundle** (`ispf pack` / deploy) + опционально **ui-pack** |
| Чего не делать? | Тяжёлая логика в виджетах, MQTT journal flood, циклы binding — [anti-patterns](../en/anti-patterns.md) |

---

## Путь A — Первый час

| Шаг | Время | Действие | Документ |
|-----|-------|----------|----------|
| 1 | 15 мин | Запуск, вход, demo sensor + dashboard | [Быстрый старт](getting-started.md) |
| 2 | 10 мин | Старт драйвера; температура / alarm | Getting started |
| 3 | 10 мин | Режим оператора (`?mode=operator`) | [Оператор](operator-guide.md) |
| 4 | 15 мин | Модель дерева | [Модель объектов](object-model.md) |
| 5 | 10 мин | Чего не строить | [Anti-patterns](../en/anti-patterns.md) |

**Готово, когда:** можете объяснить дерево → переменная → binding/alert → dashboard без Java.

---

## Путь B — День первого решения

| Шаг | Цель | Документ |
|-----|------|----------|
| 1 | Хаб + DEVICE | [Принципы приложений](application-principles.md) · [Lab training](lab-training.md) |
| 2 | Драйвер RUNNING | [Драйверы](drivers.md) |
| 3 | Binding / функция хаба | [Привязки](bindings.md) · [Язык выражений](expression-language.md) |
| 4 | Dashboard / SCADA | [Дашборды](dashboards.md) · [Виджеты](widgets.md) |
| 5 | Alert → workflow | [Автоматизация](automation.md) · [Workflows](workflows.md) |
| 6 | Pack & validate | [Разработчик решений](solution-developer-guide.md) |

---

## Curriculum — глубокий трек (текст)

После A/B — модули по порядку (читать → практика). Полная таблица с ссылками — в **[en/learn.md](../en/learn.md)#curriculum--deep-track-text**.

| # | Модуль |
|---|--------|
| 1 | Цели и слои платформы |
| 2 | Модель объектов |
| 3 | Драйверы и протоколы |
| 4 | Единая модель (хаб ≠ DEVICE) |
| 5 | Bindings и выражения |
| 6 | Хранение и historian |
| 7 | Алерты и автоматизация |
| 8 | Визуализация |
| 9 | Доступ и роли |
| 10 | Отгрузка (bundle / ui-pack) |
| 11 | Эксплуатация |
| 12 | Стандарт именования / authoring |

---

## Связанное

| Документ | Роль |
|----------|------|
| [ru/readme](readme.md) | Каталог |
| [Быстрый старт](getting-started.md) | Boot + первый логин |
| [Сертификация](certification.md) | Учебные треки |
| [en/learn.md](../en/learn.md) | Полный хаб (канон) |
