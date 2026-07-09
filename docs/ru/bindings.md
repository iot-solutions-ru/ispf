> **Язык:** русская версия (вычитка). Канонический английский: [en/bindings.md](../en/bindings.md).

﻿# Привязки цепочки (обязательные правила)

**Обязательное правило** — декларативное правило расчета значений переменных на объекте: **когда** (активаторы) → **если** (условие, CEL) → **как** (выражение) → **куда** (цель).

Правила хранения в системной переменной `@bindingRules` (JSON-массив, зарезервировано). Время выполнения — **`BindingRuleEngine`** (Единый механизм привязок с v0.8.0).

См. также: [OBJECT_MODEL.md](object-model.md), [BLUEPRINTS.md](blueprints.md), ADR [0010](decisions/0010-binding-rules-only.md).

---

## Модель `BindingRule`

```json
{
  "id": "member3-sine",
  "name": "Member3 sine",
  "enabled": true,
  "order": 20,
  "activators": {
    "onStartup": false,
    "onVariableChange": [
      { "objectPath": "root.platform.devices.virt-cluster.dev-03", "variableName": "sineWave" }
    ],
    "onEvent": null,
    "periodicMs": 0
  },
  "condition": "",
  "expression": "refAt(\"root.platform.devices.virt-cluster.dev-03\", sineWave)",
  "target": { "variableName": "member3Sine", "field": "value" }
}
```

| Поле | Назначение |
|------|------------|
| `activators.onStartup` | Пересчёт при старте сервера / attach модели |
| `activators.onVariableChange` | Список `{ objectPath, variableName }`; `"self"` + `"*"` = любая локальная переменная |
| `activators.periodicMs` | Периодический пересчет (0 = выкл.); индексируется в `platform_binding_periodic_rules`, пробуждение по `next_run_at` |
| `condition` | CEL; пусто = всегда |
| `expression` | CEL или одна platform function |
| `target` | Куда записать результат (см. **Target kinds** ниже) |
| `kind` | Опционально: `reactive` или `historian` ([ADR-0041](decisions/0041-multi-tag-historian-computations.md)) |
| `windowBucket` | Окно historian (`5m`, `1h`, …) |

Рецепты: [analytics-historian-cookbook.md](analytics-historian-cookbook.md)

### Периодическое время выполнения

Правила с `periodicMs > 0` анализа в JDBC-индексе `platform_binding_periodic_rules` при сохранении `@bindingRules`. **`BindingPeriodicScheduler`** будит JVM один раз на ближайший `next_run_at` и выполняет только правильные удары — без секундного обхода всего дерева. Если периодических правил нет, фоновый пробуждение не имеет значения.

### Виды целей (Правило платформы)

Расширение моделей — ADR [0019](decisions/0019-platform-rule-unification.md). Если `target.kind` отсутствует → **`variable`** (обратная совместимость).

| `kind` | Поля | Назначение |
|--------|------|------------|
| `variable` | `variableName`, `field` | Как сегодня — запись в переменную объекта |
| `context` | `path` (dot-notation) | Запись в `@dashboardContext` на объекте `DASHBOARD` |
| `event` | `eventName` | Публикация platform event; payload из `expression` |

Пример правила дашборда (планируется):

```json
{
  "id": "alarm-mode",
  "activators": { "onContextChange": true },
  "condition": "context.selection.device != \"\"",
  "expression": "\"alarm\"",
  "target": { "kind": "context", "path": "params.mode" }
}
```

Активатор **`onContextChange`** — пересчёт при поддержке `@dashboardContext`. Полная спецификация: [PLATFORM_LOGIC.md](platform-logic.md).

**Перекрестный объект:** активатор на удаленном пути + `refAt("path", var)` в выражении. При удаленной переменной (в т.ч. телеметрии драйвера) `BindingPropagationListener` пересчитывает правила на потребительских объектах.

**Активаторы по умолчанию** (если не заданы): `refAt` в выражении → автоматические удаленные активаторы; иначе местный `self:*`.

---

## Выражения (выражение)

Два вида (как раньше, но внутри `rule.expression`):

| Вид | Пример |
|-----|--------|
| **CEL** | `self.temperature.value + 1.0` |
| **Platform binding** | `counterRate(ifInOctets)`, `hysteresis(temperature, 80, 70)`, `refAt("root...dev-01", sineWave)` |

Валидация: `POST /api/v1/expressions/validate` или Web Console «Проверить».

Stateful bindings (`counterRate`, `hysteresis`, …) — состояние в `@bindingState` (см. прежнее поведение).

---

## ОТДЫХ API

```http
GET  /api/v1/objects/by-path/binding-rules?path={objectPath}
PUT  /api/v1/objects/by-path/binding-rules?path={objectPath}
DELETE /api/v1/objects/by-path/binding-rules/{ruleId}?path={objectPath}
```

Инструмент агента: `create_binding_rule` (путь, идентификатор, targetVariable, выражение, RemoteObjectPath?, RemoteVariableName?, условие?, onStartup?, порядок?).

---

## Модели

В модели — `ModelBindingRule` (полная схема или `ModelBindingRule.of(id, target, expression)`). При применении/создании правил меряются через `ModelBindingRulesMerger` **после** функции.

Поле `defaultBinding` на переменной модели **удалено** (v0.8.0).

---

## Обновление с v0.7.x (legacy `bindingExpression`)

Поле `bindingExpression` на переменной и колонка `binding_expr` **удалены** (0010, v0.8.0). Привязки — только `@bindingRules`.

**Prod** (`ispf.iot-solutions.ru`): PostgreSQL в Docker (`ispf-postgres`), а не H2. **Локальный разработчик:** Файл H2 или докер составляют PostgreSQL.

```bash
# Prod VPS (Docker postgres)
systemctl stop ispf-server
docker exec ispf-postgres psql -U ispf -d postgres -c 'DROP DATABASE IF EXISTS ispf;' -c 'CREATE DATABASE ispf OWNER ispf;'
systemctl start ispf-server

# Local H2: удалить ./data/ispf-local.mv.db
# Local/dev compose: docker compose exec postgres psql ...
```

Существующая БД без пересоздания: Flyway `V41__drop_binding_expr.sql` снимает колонку; при **несоответствии контрольной суммы V1** необходимо пересоздание (см. [DEPLOYMENT.md](deployment.md)).

---

## Пользовательский интерфейс

Веб-консоль → Инспектор объектов → вкладка **«Вычисления»** (reactive + historian). См. [ADR-0040](decisions/0040-unified-computations-ui.md).

---

## Не путать с

- **SQL bindings** (`ApplicationSqlBindingService`) — отдельный scheduler, не object binding rules
- **Правила оповещений**, корреляторы, рабочие процессы — разработка подсистемы
