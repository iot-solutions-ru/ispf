> **Язык:** русская версия. Канонический английский: [en/fto-object-tree-model.md](../en/fto-object-tree-model.md).

# FTO-memo — модель дерева объектов (переменные / функции / события / bindings)

> **Статус:** Internal — инженерная IP-инвентаризация для counsel. **Не юридическая консультация.**  
> **Хаб:** [doc-status.md](doc-status.md) · Связанные: [license-compliance](license-compliance.md), [object-model](object-model.md), [bindings](bindings.md).

## Цель

Зафиксировать, что **дерево объектов** ISPF (переменные / функции / события, декларативные привязки, выражения) относится к известному **классу IIoT / SCADA middleware**, и дать counsel чеклист **Freedom-to-Operate (FTO)** **без** именования отдельных коммерческих вендоров в публичных docs.

**Честность продукта:** высокоуровневая архитектура ISPF следует **индустриальному паттерну** context/object-tree платформ (иерархия узлов со свойствами, операциями, уведомлениями и декларативной связкой). Вдохновение *идеями* — нормально; counsel всё равно разделяет слои:

| Слой | Риск при ошибке |
|------|-----------------|
| **Идеи / архитектурные паттерны** | Обычно не copyright; патенты — только если claim’ы покрывают *конкретный* механизм |
| **Выражение кода, docs, API, протоколов** | Copyright / коммерческая тайна |
| **Товарные знаки / маркетинговые формулировки** | Бренд / недобросовестная конкуренция |
| **Запатентованные реализации** (если есть) | Патентное нарушение — нужен claim chart |

Этот memo — **vendor-agnostic**. Имена конкурентов — только в рабочих материалах counsel, не в публичных docs.

## Индустриальный паттерн (абстрактно)

Многие integration / SCADA / IoT платформы используют сходную абстрактную модель:

- Иерархическое **дерево объектов / контекстов / активов**
- На узле: **variables** (состояние), **functions** (операции), **events** (уведомления) + metadata
- Опционально единое **структурированное** представление значений (records / tables / schemas)
- **Bindings** — выражения с триггерами (startup / change / event / periodic)
- **Models / digital twins** — шаблоны или relative-модели с производным V/F/E
- Drivers / agents, которые **нормализуют** разнородные устройства в общее дерево

Некоторые вендоры маркетингово называют части этого паттерна «собственными» или «патентованными». Маркетинг ≠ объём claim’ов. Counsel должен искать реальные патенты (если есть) в юрисдикциях продаж.

## Сторона ISPF (что поставляем)

Канонические docs: [object-model](object-model.md), [bindings](bindings.md), [application-principles](application-principles.md).

| Концепция | Реализация ISPF |
|-----------|-----------------|
| Иерархия | Dot-path **object tree** (`root.platform…`) |
| Содержимое узла | **Variables**, **Functions**, **Events** (+ blueprints / instance types) |
| Типизированные значения | `DataSchema` / `DataRecord` (fields + rows) |
| Логика | Декларативные **`@bindingRules`** + **CEL** ([bindings](bindings.md)) |
| Device vs logic | Жёсткое правило: оркестрация / twin-логика — **не** `ObjectType.DEVICE` ([AGENTS.md](../../AGENTS.md), [application-principles](application-principles.md)) |
| Драйверы | Отдельные **driver packs**, Apache-2.0 clean-room кодеки там, где раньше были GPL-стеки |
| Лицензия | Платформа **AGPL-3.0** + опциональный Enterprise dual-license |

## Сходство с классом продуктов (честно)

| Тема | Пересечение с классом | Дифференциация ISPF (для FTO) |
|------|----------------------|-------------------------------|
| Дерево объектов/контекстов | **Высокое** | Давний паттерн (см. prior art); пути/API/docs ISPF — собственные |
| Variables + functions + events на узле | **Высокое** | Тот же *класс*, что OOP device models; дескрипторы, REST и хранение — собственные |
| Bindings / выражения | **Высокое (роль)** | ISPF: **CEL** + `@bindingRules` JSON |
| «Нормализовать устройства в одну модель» | **Высокое (роль)** | Drivers → tree; собственные pack protocol и кодеки |
| Универсальный tabular wire-тип | **Низкое–среднее** | ISPF: schema/record; не чужой table-протокол |
| Терминология | Осторожно | Термины ISPF: *object tree*, *binding rules*, *blueprints* — не копировать чужие слоганы и doc-примеры |

**Вердикт (инженерия, не counsel):** сходство с другими object-tree платформами на уровне категории **ожидаемо**. Само по себе оно **не доказывает** патентное или copyright-нарушение. Остаточный риск — **claim-specific** и **copy-specific**.

## Якоря prior art (для brief counsel)

Неполный список; counsel расширяет:

1. **OPC UA** AddressSpace — nodes, variables, methods, events.
2. **BACnet** object model — properties, services, event reporting.
3. **SNMP** MIB / managed objects.
4. **JavaBeans / .NET components / OSGi** — properties, methods, events.
5. **AWS IoT Device Shadow / Azure Digital Twins / DTDL** — twin graphs + properties.
6. **Ignition / Node-RED / Home Assistant** — tags, bindings, expressions.
7. **CEL** (Common Expression Language) — открытая спецификация Google, используемая bindings ISPF.

Аргумент: V/F/E-на-дереве — **давний индустриальный паттерн**, а не уникальное изобретение одного вендора.

## Чеклист counsel (FTO)

### A. Патентный поиск и claim chart

- [ ] Поиск патентов/заявок в юрисдикциях продаж (**RU**, **ЕАЭС**, экспорт по необходимости) по claim’ам: иерархические context/object trees с variables/functions/events; нормализация устройств в единую модель; декларативные bindings с expression language; универсальные structured/table типы значений.
- [ ] Assignee-sweep по крупным коммерческим object-tree IIoT вендорам — **только в рабочих материалах counsel** (не перечислять конкурентов в публичных docs).
- [ ] По каждому действующему патенту: сопоставить **independent claims** с модулями ISPF (`object tree`, `BindingRuleEngine`, CEL, driver packs, historian).
- [ ] Отметить claim’ы, требующие **vendor-specific protocol**, **universal data-table wire format** или иного элемента, которого нет в ISPF — вероятно non-overlap.
- [ ] Проверить срок, семью, оппозицию/недействительность и prior art.

### B. Copyright / clean-room гигиена

- [ ] Подтвердить: в репозитории нет чужих проприетарных исходников, agent SDK или закрытых docs.
- [ ] Подтвердить: UI-строки, doc-примеры и туториалы **не** дословно с документации третьих сторон.
- [ ] Держать короткий **provenance note** по object model (industry pattern; независимая реализация; список prior-art refs).
- [ ] CLA уже позволяет dual-licensing ([CLA.md](../../CLA.md)); убедиться, что контрибьюторы не вставляли чужой проприетарный код.

### C. Товарный знак / go-to-market

- [ ] Не использовать чужие товарные знаки продуктов/компаний (и сходные до степени смешения) в имени продукта или домене ISPF.
- [ ] Маркетинг: описывать ISPF своими терминами; сравнительные таблицы допустимы, если фактичны и не нарушают локальное право.
- [ ] Не заявлять «совместим с &lt;vendor&gt;», пока нет осознанного лицензированного interop.

### D. Договор / Enterprise EULA

- [ ] Enterprise EULA: IP-warranty не шире, чем одобрит counsel; опционально FTO-приложение по юрисдикциям.
- [ ] Bundle/driver SKU — на отдельных EULA ([commercial-licensing](commercial-licensing.md)).

## Безопасная инженерная / docs-практика

1. **Держать видимыми дифференцирующие решения** в ADR (DEVICE vs logic objects, CEL, binding-rules-only, driver packs).
2. **Не ребрендить** фичи ISPF чужими маркетинговыми слоганами.
3. При добавлении фич, типичных для object-tree класса (models, relative twins, concurrency bindings), фиксировать **independent design rationale** в ADR.
4. Пересматривать memo при выходе на новую страну или крупный exclusive-контракт.
5. **Именованные патентные сравнения с конкурентами** не держать в публичных docs и marketing на `main`; только в annex’ах counsel.

## Рекомендуемый deliverable counsel

Краткое письменное заключение:

1. Patent FTO (RU + основные экспортные рынки) для object-tree + V/F/E + bindings.
2. Claim charts по найденным патентам класса object-tree / device-normalization (рабочие материалы counsel).
3. Sign-off по copyright/trademark hygiene для docs и UI.
4. Опционально: defensive publication / регистрация ПО в Роспатенте (свидетельство о регистрации программы — **не** патент на изобретение).

## Связанные документы

- [license-compliance](license-compliance.md)
- [object-model](object-model.md)
- [bindings](bindings.md)
- [pid-symbols-legal](pid-symbols-legal.md)
- [russian-software-registry](russian-software-registry.md)
- [CLA.md](../../CLA.md)
