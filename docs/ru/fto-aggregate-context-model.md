> **Язык:** русская версия. Канонический английский: [en/fto-aggregate-context-model.md](../en/fto-aggregate-context-model.md).

# FTO-memo — модель дерева объектов / контекстов vs AggreGate

> **Статус:** Internal — инженерная IP-инвентаризация для counsel. **Не юридическая консультация.**  
> **Хаб:** [doc-status.md](doc-status.md) · Связанные: [license-compliance](license-compliance.md), [object-model](object-model.md), [bindings](bindings.md).

## Цель

Зафиксировать **концептуальное пересечение** между деревом объектов ISPF (переменные / функции / события, привязки, выражения) и публично описанной Unified Data Model Tibbo **AggreGate**, и дать counsel чеклист **Freedom-to-Operate (FTO)**.

**Честность продукта:** высокоуровневая архитектура ISPF **вдохновлялась** индустриальным паттерном, который популяризировал AggreGate (иерархия контекстов с переменными, функциями, событиями и декларативными привязками). Вдохновение *идеями* — нормально; counsel всё равно разделяет слои:

| Слой | Риск при ошибке |
|------|-----------------|
| **Идеи / архитектурные паттерны** | Обычно не copyright; патенты — только если claim’ы покрывают *конкретный* механизм |
| **Выражение кода, docs, API, протоколов** | Copyright / коммерческая тайна |
| **Товарные знаки / маркетинговые формулировки** | Бренд / недобросовестная конкуренция |
| **Запатентованные реализации** (если есть) | Патентное нарушение — нужен claim chart |

## Публичные заявления AggreGate (маркетинг)

Источники (публичный веб; перед подачей в counsel проверить даты/URL):

- [Unified Data Model](https://aggregate.digital/technology/architecture/unified-data-model.html) — маркетинг называет **object normalization** «патентованной»: иерархия **contexts**; у каждого **variables**, **functions**, **events** + metadata.
- Универсальный тип: почти всё — **data table** (значения переменных, I/O функций, payload событий).
- [Bindings](https://aggregate.digital/technology/analytics/bindings.html) — выражения, триггеры (startup / change / event / periodic), связка UI и моделей.
- [Models / digital twins](https://aggregate.digital/technology/analytics/models.html) — relative/absolute models с V/F/E + bindings.
- Ранние материалы Tibbo исторически использовали формулировку **patent-pending** для device-as-object (properties / methods / events).

**Инженерная оговорка:** маркетинг «патентовано» ≠ найденные номера патентов и объём claim’ов. Лёгкий публичный поиск **не подтвердил** надёжные номера патентов Tibbo/AggreGate на «object normalization». Counsel должен искать в Роспатенте / USPTO / EPO / CNIPA по assignee **Tibbo**, **AggreGate**, связанным лицам и изобретателям.

## Сторона ISPF (что поставляем)

Канонические docs: [object-model](object-model.md), [bindings](bindings.md), [application-principles](application-principles.md).

| Концепция | Реализация ISPF |
|-----------|-----------------|
| Иерархия | Dot-path **object tree** (`root.platform…`) |
| Содержимое узла | **Variables**, **Functions**, **Events** (+ blueprints / instance types) |
| Типизированные значения | `DataSchema` / `DataRecord` (fields + rows) — **не** «всё = data table AggreGate» |
| Логика | Декларативные **`@bindingRules`** + **CEL** ([bindings](bindings.md)) |
| Device vs logic | Жёсткое правило: оркестрация / twin-логика — **не** `ObjectType.DEVICE` ([AGENTS.md](../../AGENTS.md), [application-principles](application-principles.md)) |
| Драйверы | Отдельные **driver packs**, Apache-2.0 clean-room кодеки там, где раньше были GPL-стеки |
| Лицензия | Платформа **AGPL-3.0** + опциональный Enterprise dual-license |

## Карта сходства (честно)

| Тема | Пересечение | Дифференциация (зафиксировать для FTO) |
|------|-------------|----------------------------------------|
| Дерево объектов/контекстов | **Высокое** | Индустриальный паттерн (OPC UA, BACnet, NMS, digital twins); пути/API/docs ISPF — собственные |
| Variables + functions + events на узле | **Высокое** | Тот же *класс*, что OOP device models; дескрипторы, REST и хранение ISPF — собственные |
| Bindings / выражения | **Высокое (роль)** | ISPF: **CEL** + `@bindingRules` JSON; не язык выражений и не UI bindings AggreGate |
| «Нормализовать любое устройство в одну модель» | **Высокое (роль)** | Drivers → tree; нет протокола AggreGate Agent |
| Универсальный тип data table | **Низкое** | ISPF: schema/record; таблицы не единственный wire-тип |
| Терминология | Частичное | Предпочитать термины ISPF: *object tree*, *binding rules*, *blueprints* — **избегать** слоганов AggreGate (*object normalization*, *context tree* как продуктовый claim, копирования их doc-примеров) |

**Вердикт (инженерия, не counsel):** сходство на уровне категории **реальное и признано**. Само по себе оно **не доказывает** патентное или copyright-нарушение. Остаточный риск — **claim-specific** (патенты) и **copy-specific** (код/docs/API).

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

- [ ] Поиск патентов/заявок: assignee/applicant Tibbo Technology, AggreGate, связанные RU/TW/US лица; keywords: *context tree*, *object normalization*, *device normalization*, *unified data model*, *bindings*, *data table*.
- [ ] Юрисдикции продаж: **RU**, **ЕАЭС**, экспортные рынки (US/EU/CN по необходимости).
- [ ] По каждому действующему патенту: сопоставить **independent claims** с модулями ISPF (`object tree`, `BindingRuleEngine`, CEL, driver packs, historian).
- [ ] Отметить claim’ы, требующие **универсальной data-table** семантики или **конкретного agent protocol** — вероятно non-overlap, если у ISPF этого нет.
- [ ] Проверить срок, семью, оппозицию/недействительность и prior art.

### B. Copyright / clean-room гигиена

- [ ] Подтвердить: в репозитории нет исходников AggreGate/Tibbo, Agent SDK или проприетарных docs.
- [ ] Подтвердить: UI-строки, doc-примеры и туториалы **не** дословно с страниц AggreGate.
- [ ] Держать короткий **provenance note** по object model (inspired by industry pattern; независимая реализация; список prior-art refs).
- [ ] CLA уже позволяет dual-licensing ([CLA.md](../../CLA.md)); убедиться, что контрибьюторы не вставляли чужой проприетарный код.

### C. Товарный знак / go-to-market

- [ ] Не использовать **AggreGate**, **Tibbo** и сходные до степени смешения знаки в имени продукта или домене.
- [ ] Маркетинг: описывать ISPF своими терминами; сравнительные таблицы допустимы, если фактичны и не нарушают локальное право о дискредитации.
- [ ] Не заявлять «совместим с AggreGate», пока нет осознанного лицензированного interop.

### D. Договор / Enterprise EULA

- [ ] Enterprise EULA: IP-warranty не шире, чем одобрит counsel; опционально FTO-приложение по юрисдикциям.
- [ ] Bundle/driver SKU — на отдельных EULA ([commercial-licensing](commercial-licensing.md)).

## Безопасная инженерная / docs-практика

1. **Держать видимыми дифференцирующие решения** в ADR (DEVICE vs logic objects, CEL, binding-rules-only, driver packs).
2. **Не ребрендить** фичи ISPF маркетинговыми терминами AggreGate.
3. При добавлении «AggreGate-like» фич (models, relative twins, concurrency bindings) фиксировать **independent design rationale** в ADR.
4. Пересматривать memo при выходе на новую страну или крупный exclusive-контракт.

## Рекомендуемый deliverable counsel

Краткое письменное заключение:

1. Patent FTO (RU + основные экспортные рынки) для object-tree + V/F/E + bindings.
2. Подтверждение, что публичное «patented object normalization» ложится (или не ложится) на enforceable claims против ISPF.
3. Sign-off по copyright/trademark hygiene для docs и UI.
4. Опционально: defensive publication / регистрация ПО в Роспатенте (свидетельство о регистрации программы — **не** патент на изобретение).

## Связанные документы

- [license-compliance](license-compliance.md)
- [object-model](object-model.md)
- [bindings](bindings.md)
- [pid-symbols-legal](pid-symbols-legal.md)
- [russian-software-registry](russian-software-registry.md)
- [CLA.md](../../CLA.md)
