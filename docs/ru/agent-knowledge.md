> **Язык:** русская версия. Английский оригинал: [en/agent-knowledge.md](../en/agent-knowledge.md).

# База знаний и карта маршрутизации для инженеров и ИИ-агента (`AGENT_KNOWLEDGE`)

> **Статус:** Internal — сводная карта архитектуры решений, инструментов платформы и тем поиска `search_context` для инженеров, ИИ-студии и клиентов MCP. Метки зрелости: [doc-status.md](doc-status.md).

Этот документ служит единым справочником-маршрутизатором: он показывает, **какими механизмами собираются прикладные решения в ISPF**, где находится каждая функция в инструментах агента и REST API и какой раздел документации открыть по каждой задаче.

**Связанные разделы:** [Правила архитектуры приложений (`application-principles.md`)](application-principles.md) · [Частые ошибки проектирования (`anti-patterns.md`)](anti-patterns.md) · [ИИ-студия и адаптер MCP (`ai-development.md`)](ai-development.md) · [ADR-0001](decisions/0001-app-platform-boundary.md) · [ADR-0005](decisions/0005-tree-first-ai-agent.md) · [ADR-0051](decisions/0051-poka-yoke-constraints-over-guards.md).

---

## 1. Главные архитектурные правила ISPF

1. **Прикладная логика живёт в дереве объектов (`root.platform.*`), а не в коде сервера:** ни инженер, ни ИИ-агент не добавляют отраслевой Java-код в `ispf-server` и не добавляют отраслевые страницы в `apps/web-console`.
2. **Узел логики не может иметь тип `ObjectType.DEVICE` (`LOGIC_HOST_DEVICE`):**
   - уникальный центральный координатор приложения или станции — шаблон **`SINGLETON`** (`root.platform.singleton-blueprints.<имя>`, инструмент `ensure_singleton_instance`);
   - повторяемый цифровой двойник линии или агрегата с собственной логикой — шаблон **`INSTANCE`** (`instantiate_instance_type`);
   - полевые датчики и ПЛК — узлы **`DEVICE`** (`create_virtual_device`, `configure_driver`), отвечающие только за телеметрию и команды ввода-вывода.
3. **Единая среда выполнения:** все функции, правила расчёта (`@bindingRules`), тревоги (`ALERT`), корреляторы (`CORRELATOR`), дашборды (`DASHBOARD`), мнемосхемы (`MIMIC`) и процессы (`WORKFLOW`) адресуются по путям дерева объектов.
4. **Таблица `applications` — это реестр и изолированная SQL-схема приложения (`app_<appId>`),** а не отдельный движок вне дерева. Системные миграции Flyway используются только для таблиц самого ядра платформы.

---

## 2. Четыре стадии работы с решением и способы сборки

| Стадия | Назначение | Штатный механизм ISPF |
|--------|------------|-----------------------|
| **1. Проектирование и отладка (`AUTHOR`)** | Создание и проверка узлов на живом дереве | Консоль администратора, пошаговый ИИ-агент или внешняя IDE по протоколу MCP |
| **2. Типизация (`SHAPE`)** | Выделение повторяемой структуры в шаблоны | Шаблоны `SINGLETON`, `INSTANCE` и примеси `MIXIN` ([Шаблоны объектов](blueprints.md)) |
| **3. Поставка и перенос (`SHIP`)** | Воспроизводимая упаковка проекта в файл или архив | Манифест **`bundle.json`** (`ispf pack` / `POST /api/v1/applications/{id}/deploy`) + при необходимости пакет интерфейса **`ui-pack`** |
| **4. Точечное продвижение правок (`PROMOTE`)** | Контролируемое применение пачки правок на действующем дереве | Пакеты изменений **`change-sets`** с проверкой ревизий узлов ([Совместная работа](collaboration.md)) |

### Сводная таблица вариантов сборки прикладного решения (A–I)

| Вариант | Название | Когда применять | Основные инструменты / API | Экран оператора |
|---------|----------|-----------------|----------------------------|-----------------|
| **A** | **Пошаговая сборка на дереве (`Tree-first`)** | Быстрый прототип, стенд, интерактивная работа с ИИ-агентом | `ensure_singleton_instance`, `create_virtual_device`, `configure_driver`, `add_dashboard_widget` | `configure_operator_ui` |
| **B** | **Ручная сборка в консоли администратора** | Настройка инженером через визуальные редакторы браузера | Проводник, Редактор шаблонов, Конструктор дашбордов, Инспектор | Раздел `root.platform.operator-apps` |
| **C** | **Пакетное развёртывание (`bundle.json`)** | Промышленная поставка, хранение в Git, CI/CD | `POST /api/v1/applications/{id}/deploy` или `import_package` | Секция `operatorUi` в манифесте |
| **D** | **Пошаговый вызов REST API приложения** | Скрипты автоматизации и поэтапная интеграция | `register` → `data/migrate` → `functions/deploy` → `bindings/deploy` | `PUT /api/v1/operator-apps/{id}/ui` |
| **E** | **Генерация пакета в ИИ-студии** | Создание черновика `bundle.json` по текстовому ТЗ | `validate_bundle` → `dry_run_deploy` → `import_package` | Из сгенерированного `operatorUi` |
| **F** | **Адаптация эталонного примера (`examples/*`)** | Быстрый старт на базе готового образца SCADA / MES / ОВиК | `list_examples`, `get_example_bundle` → доработка → импорт | Из манифеста примера |
| **G** | **Мониторинг без собственных SQL-таблиц** | Диспетчеризация оборудования без табличного учёта заказов | Устройства + правила расчёта + дашборды на дереве | Встроенное приложение `platform` |
| **H** | **Лицензируемый коммерческий пакет** | Защищённая поставка партнёра или интегратора | Манифест `bundle.json` с RSA-подписью в блоке `license` ([commercial-licensing.md](commercial-licensing.md)) | Как в варианте C |
| **I** | **Собственное React-приложение (`ui-pack`)** | Брендированный веб-интерфейс оператора на React / Vite | Сборка SPA в Cursor / VS Code (`artifactKind: "ui-pack"`) + вызов функций через `POST /api/v1/bff/invoke` ([external-ide.md](external-ide.md)) | Открывается по адресу `/apps/<appId>/` |

---

## 3. Восемь слоёв полноценного проекта на дереве объектов

При сборке комплексного решения на живом дереве соблюдайте последовательность из 8 слоёв (`get_automation_schema topic=projectBlueprint`):

| № | Слой проекта | Путь в дереве объектов | Инструменты агента |
|---|--------------|------------------------|--------------------|
| **1** | **Узел логики (`SINGLETON` или `INSTANCE`)** | `root.platform.singleton-blueprints.<проект>-hub` (или экземпляр `INSTANCE`) | `ensure_singleton_instance`, `instantiate_instance_type`, `create_binding_rule` |
| **2** | **Устройства телеметрии (`DEVICE`)** | `root.platform.devices.<проект>.*` | `create_virtual_device`, `configure_driver`, `driver_control`, `apply_mixin_blueprint` |
| **3** | **Экраны оператора (`DASHBOARD`)** | `root.platform.dashboards.<проект>-*` | `set_dashboard_layout`, `add_dashboard_widget` |
| **4** | **Мнемосхемы SCADA (`MIMIC`)** | `root.platform.mimics.<проект>-*` | `save_mimic_diagram`, `add_mimic_elements`, `get_mimic_diagram` |
| **5** | **Правила тревог (`ALERT`)** | `root.platform.alert-rules.<проект>-*` | `configure_alert` |
| **6** | **Корреляторы событий (`CORRELATOR`)** | `root.platform.correlators.<проект>-*` | `configure_correlator` |
| **7** | **Процессы смены (`WORKFLOW`)** | `root.platform.workflows.<проект>-*` | `save_workflow_bpmn`, `update_workflow_status` |
| **8** | **Отчёты (`REPORT`)** | `root.platform.reports.<проект>-*` | `configure_report`, `run_report` |

---

## 4. Каталог инструментов ИИ-агента по областям

| Область | Инструменты |
|---------|-------------|
| **Поиск знаний и примеров** | `search_context`, `list_drivers`, `get_driver_help`, `list_examples`, `get_example_bundle`, `get_widget_catalog`, `list_applications`, `search_platform_recipes` |
| **Дерево объектов и шаблоны** | `list_objects`, `get_object`, `create_object`, `delete_object`, `search_objects`, `search_by_haystack_tags`, `list_object_models`, `list_relative_models`, `list_instance_types`, `list_absolute_models`, `apply_relative_model`, `instantiate_instance_type`, `ensure_singleton_instance`, `create_virtual_device` |
| **Переменные и история** | `list_variables`, `describe_variables`, `set_variable`, `create_variable`, `configure_variable_history` |
| **Драйверы протоколов** | `configure_driver`, `driver_control` (`start`, `stop`, `poll`, `status`) |
| **Правила расчёта (`Bindings`)** | `create_binding_rule`, `list_binding_rules`, `configure_platform_context_rule` |
| **Дашборды и мнемосхемы SCADA** | `get_dashboard_layout`, `set_dashboard_layout`, `add_dashboard_widget`, `list_mimic_symbols`, `save_mimic_diagram`, `add_mimic_elements`, `get_mimic_diagram` |
| **Тревоги, корреляторы и процессы BPMN** | `configure_alert`, `configure_correlator`, `list_automation`, `get_automation_schema`, `get_workflow`, `save_workflow_bpmn`, `run_workflow`, `update_workflow_status`, `list_workflow_instances`, `signal_workflow_instance`, `cancel_workflow_instance` |
| **Функции, события и отчёты** | `list_functions`, `get_function`, `get_function_template`, `deploy_tree_function`, `invoke_tree_function`, `invoke_bff`, `fire_event`, `list_events`, `list_event_catalog`, `get_event_schema`, `list_reports`, `get_report_schema`, `run_report`, `configure_report` |
| **Прикладные пакеты (`bundle.json`)** | `register_application`, `application_data_migrate`, `application_data_seed`, `deploy_app_binding`, `deploy_app_function`, `validate_bundle`, `dry_run_deploy`, `import_package`, `export_application_bundle`, `rollback_application_deploy`, `pull_application_from_tree` |
| **Пульт оператора и расписания** | `configure_operator_ui`, `list_platform_schedules`, `configure_platform_schedule`, `resolve_timezone`, `export_haystack` |

---

## 5. Какой механизм выбрать для конкретной задачи

| Задача | Штатный механизм ISPF | Раздел документации |
|--------|-----------------------|---------------------|
| Вычисление производной переменной или сглаживание сигнала | Правило расчёта в `@bindingRules` (CEL или встроенная функция) | [Правила расчёта (`bindings.md`)](bindings.md), [Язык выражений (`expression-language.md`)](expression-language.md) |
| Расчёт агрегата по истории за смену или час (среднее, OEE) | Историческое правило расчёта (`kind: "historian"`) | [Рецепты исторических расчётов (`analytics-historian-cookbook.md`)](analytics-historian-cookbook.md) |
| Показ/скрытие панелей и смена режима на экране оператора | Правило на объекте `DASHBOARD` с записью в `@dashboardContext` | [Единая модель правил (`platform-logic.md`)](platform-logic.md) |
| Контроль выхода параметра за уставку с гистерезисом | Узел правила тревоги `ALERT` | [Автоматизация (`automation.md`)](automation.md) |
| Обнаружение серии или цепочки событий во времени | Узел коррелятора `CORRELATOR` | [Автоматизация (`automation.md`)](automation.md) |
| Пошаговый регламент с выдачей задачи дежурному оператору | Процесс BPMN (`WORKFLOW`) + очередь задач (`Work Queue`) | [Процессы BPMN (`workflows.md`)](workflows.md) |
| Чтение и запись прикладных таблиц БД (заказы, партии, справочники) | Скриптовая функция на узле `SINGLETON` / `INSTANCE`, вызываемая через `POST /api/v1/bff/invoke` | [Прикладные решения (`applications.md`)](applications.md), [Функции на объектах (`object-functions.md`)](object-functions.md) |
| Периодическая подкачка скалярного агрегата из SQL в переменную | SQL-привязка `sqlBinding('appId', 'varName')` | [Прикладные решения (`applications.md`)](applications.md) |
| Опрос контроллера по промышленному протоколу | Драйвер на узле `DEVICE` + привязка регистров `pointMappings` | [Драйверы устройств (`drivers.md`)](drivers.md) |
| Масштабируемая векторная мнемосхема P&ID или однолинейная схема | Узел `MIMIC` + виджет `scada-mimic` | [Мнемосхемы SCADA (`scada.md`)](scada.md), [Формат `diagramJson` (`scada-mimic.md`)](scada-mimic.md) |
| Кастомный операторский веб-интерфейс на React | Внешний пакет `ui-pack` на маршруте `/apps/<appId>/` | [Разработка во внешней IDE (`external-ide.md`)](external-ide.md) |

---

## 6. Справочник тем для быстрого поиска в базе знаний (`search_context`)

ИИ-агент и MCP-клиенты используют инструмент `search_context(query=..., topic=...)` со следующими стандартными темами:

| Тема (`topic`) | Какие документы и разделы охватывает |
|----------------|--------------------------------------|
| `application-principles` | Архитектурные правила P1–P10, разделение узлов логики (`SINGLETON` / `INSTANCE`) и телеметрии (`DEVICE`) — [application-principles.md](application-principles.md) |
| `poka-yoke` | Предотвращение ошибок схемами валидации вместо эвристик — [ADR-0051](decisions/0051-poka-yoke-constraints-over-guards.md), [anti-patterns.md](anti-patterns.md) |
| `agent-knowledge` | Варианты сборки решений A–I и общая карта документации (этот документ) |
| `external-ide` | Разработка React SPA в Cursor / VS Code, упаковка `ui-pack` и вызовы BFF — [external-ide.md](external-ide.md) |
| `applications` | Манифест `bundle.json`, миграции SQL, функции, расписания и Git — [applications.md](applications.md) |
| `public-api` | Стабильный контракт разработчика прикладных решений — [solution-developer-public-api.md](solution-developer-public-api.md) |
| `solution` | Пошаговое руководство по созданию решения с нуля — [solution-developer-guide.md](solution-developer-guide.md) |
| `dashboards` | Сетка `84×8`, виджеты, `selectionKey` и контекст экрана — [dashboards.md](dashboards.md), [widgets.md](widgets.md) |
| `scada` | Мнемосхемы `MIMIC`, библиотека символов и виджет `scada-mimic` — [scada.md](scada.md), [scada-mimic.md](scada-mimic.md) |
| `platform-logic` | Правила `КОГДА → ЕСЛИ → ТО` и переменная `@dashboardContext` — [platform-logic.md](platform-logic.md) |
| `bindings` | Реактивные и исторические правила расчёта, язык CEL и адресация `PlatformRef` — [bindings.md](bindings.md), [expression-language.md](expression-language.md) |
| `drivers` | Настройка 160+ драйверов устройств и привязка точек — [drivers.md](drivers.md) |
| `workflows` | Процессы BPMN, шаги `serviceTask` / `userTask`, таймеры и вебхуки — [workflows.md](workflows.md) |
| `automation` | Правила тревог (`ALERT`) и корреляторы событий (`CORRELATOR`) — [automation.md](automation.md) |
| `semantic` | Разметка оборудования тегами Project Haystack и классами Brick Schema — [semantic-demo.md](semantic-demo.md) |
| `timezones` | Хранение времени в UTC и часовые пояса пользователей и объектов — [ADR-0020](decisions/0020-time-and-timezones.md) |
| `web-console` | Устройство консоли администратора и пульта оператора — [web-console.md](web-console.md), [operator-guide.md](operator-guide.md) |
| `observability` | Метрики Prometheus, экспорт OpenTelemetry и диагностика нагрузки — [observability.md](observability.md) |
| `cluster` | Многоузловой кластер, роли реплик и синхронизация через NATS/Redis — [cluster.md](cluster.md) |
| `object-model` | Дерево объектов, типы узлов и схемы переменных — [object-model.md](object-model.md), [blueprints.md](blueprints.md) |
| `ai` | Инструменты ИИ-агента, `ContextPack` и протокол MCP — [ai-development.md](ai-development.md), [ai-agent.md](ai-agent.md) |
| `all` | Поиск сразу по всем разделам документации |

---

## 7. Краткие чеклисты самопроверки перед завершением задачи

### При сборке прикладного пакета (`bundle.json`)
1. Все функции приложения и сводные расчёты размещены на узле `SINGLETON` или `INSTANCE`, а не на `DEVICE`.
2. Таблицы приложения создаются в секции `migrations[]` изолированной схемы `schemaName`, а не в системном Flyway.
3. Выполнены проверки `validate_bundle` (или `ispf validate --strict`) и `dry_run_deploy` без ошибок.
4. Настроена секция `operatorUi` (или пакет `ui-pack`), чтобы оператор мог открыть пульт по адресу `?mode=operator&app=<appId>`.

### При сборке мнемосхемы SCADA (`MIMIC`)
1. Создан узел `MIMIC` в ветке `root.platform.mimics.*`.
2. Вызван инструмент `save_mimic_diagram` с непустым массивом элементов `elements[]` (насосы, резервуары, задвижки, трубопроводы, подписи) и привязками к реальным переменным устройств.
3. Проверено через `get_mimic_diagram`, что `elementCount > 0`.
4. На дашборде оператора добавлен виджет `scada-mimic` с указанием `mimicPath`.
