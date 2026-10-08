> **Язык:** зеркало. Канон: [en/learn.md](../en/learn.md).

# Обучение ISPF — прозрачный вход

> **Статус:** Stable — хаб обучения. Теги: [doc-status.md](doc-status.md).

**Одна страница: где вы сейчас и что открыть дальше.**  
Остальные гайды, лабы и сертификация висят на этой карте.

Ясный каркас обучения: Getting started → Quick start → Tutorials → How-to → Troubleshooting. У ISPF та же ясность плюс безопасные дефолты ([anti-patterns](../en/anti-patterns.md)).

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

Полная карта путей B/C, туториалы и topic map — в **английском каноне**: [en/learn.md](../en/learn.md).

---

## Связанное

| Документ | Роль |
|----------|------|
| [ru/readme](readme.md) | Каталог |
| [Быстрый старт](getting-started.md) | Boot + первый логин |
| [Сертификация](certification.md) | Учебные треки |
| [en/learn.md](../en/learn.md) | Полный хаб (канон) |
