> **Язык:** русская версия. Английский оригинал: [en/ai-development.md](../en/ai-development.md).

# ИИ-студия, агент дерева объектов и адаптер MCP

> **Статус:** Beta — встроенная ИИ-студия, пакет знаний `ContextPack`, пошаговый агент дерева объектов и протокол MCP для внешних IDE. Метки зрелости: [doc-status.md](doc-status.md).

ISPF включает встроенные средства автоматизации разработки решений с помощью языковых моделей (LLM). ИИ-агент не пишет произвольный Java-код в ядро сервера: он читает структурированную базу знаний платформы (`ContextPack`) и собирает решение штатными механизмами ISPF — создаёт узлы в дереве объектов, настраивает шаблоны, правила расчёта, тревоги и дашборды или формирует проверенный пакет `bundle.json`.

**Связанные разделы:** [Работа с ИИ-агентом и генератором решений](ai-agent.md) · [Разработка React-интерфейсов во внешней IDE (Cursor / VS Code)](external-ide.md) · [ADR-0004](decisions/0004-ai-artifact-generation-gates.md) · [ADR-0005](decisions/0005-tree-first-ai-agent.md) · [ADR-0051](decisions/0051-poka-yoke-constraints-over-guards.md).

---

## 1. Архитектура подсистемы ИИ

| Модуль / компонент | Расположение в коде | Назначение |
|--------------------|---------------------|------------|
| Интерфейс провайдера (`LlmProvider` SPI) | `packages/ispf-ai-api`, `ispf-ai-openai-compatible`, `ispf-ai-ollama` | Подключение внешних и локальных языковых моделей (OpenAI-совместимые API, Ollama) |
| Пакет знаний (`ContextPack`) | `ai/context/`, `tools/ai-pack/build.py` | Скомпилированный справочник схем `bundle.json`, шагов скриптов, виджетов, драйверов, примеров и фрагментов документации |
| Реестр инструментов (`AiToolRegistry`) | `com.ispf.server.ai.tool.AiToolRegistry`, `/api/v1/ai/tools/**` | Каталог типизированных инструментов платформы и проверка пакетов |
| ИИ-студия (`AI Studio`) | Вкладка **ИИ-студия** в `apps/web-console` | Рабочее место администратора: чат с агентом, генерация `bundle.json`, настройки модели и метрики |
| Пошаговый агент дерева (`TreeFirstAgentService`) | `packages/ispf-ai-agent`, `/api/v1/ai/agent/**` | Выполнение многошаговых задач непосредственно в дереве объектов с сохранением сессии в PostgreSQL |
| Адаптер MCP | `com.ispf.server.ai.mcp.*`, `/api/v1/ai/mcp` | Предоставление тех же инструментов внешним средам разработки (Cursor, VS Code, Claude Desktop) по протоколу MCP |

---

## 2. Пакет знаний платформы (`ContextPack`)

Пакет знаний собирается при сборке проекта из актуальной документации и эталонных примеров `examples/*/bundle.json`:

```bash
python tools/ai-pack/build.py
```

Скрипт формирует файл `packages/ispf-ai-agent/src/main/resources/ai/context-pack.json`, содержащий:
- схемы манифеста `bundle.json`, шагов скриптовых функций и типов виджетов;
- каталог драйверов (`driverCatalog`), список возможностей (`featureIndex`) и сводки примеров (`exampleSummaries`);
- разбитую на фрагменты документацию (`docChunks`) с векторными и лексическими индексами для гибридного поиска (`search_context`, [ADR-0077](../en/decisions/0077-contextpack-hybrid-retrieval.md)).

### Автоматическая сводка о сервере (`PlatformBriefingService`)

На каждом ходе агент получает компактный **Knowledge index** (число и id загруженных драйверов, счётчики виджетов/примеров, указатели на тулы). Полный справочник (построчный список драйверов, examples, featureIndex) — по умолчанию только на первом ходе; живой снимок дерева обновляется всегда. Каталожные тулы (`list_drivers`, `get_widget_catalog`, `list_object_blueprints`) перед окном LLM уплотняются; `get_example_bundle` сохраняет тела секций, если задан `sections`. В историю уходят текст пользователя и короткое summary ассистента — не сырой JSON тулов.

```yaml
ispf:
  ai:
    briefing-max-chars: 32768
    briefing-every-turn: false   # true = полный static dump каждый ход; по умолчанию полный dump только на первом
```

---

## 3. REST API проверки и генерации пакетов (`/api/v1/ai/tools/**`)

| Метод и путь | Назначение |
|--------------|------------|
| `GET /api/v1/ai/tools/context-pack` | Метаданные пакета знаний и живая сводка по серверу |
| `POST /api/v1/ai/tools/context-pack/refresh` | Сброс кэша пакета знаний и обновление живого снимка (роль `admin`) |
| `POST /api/v1/ai/tools/validate-bundle` | Полная проверка `bundle.json` без записи в БД |
| `POST /api/v1/ai/tools/dry-run-deploy` | Пробный прогон установки со списком изменений (`wouldApply`) |
| `GET /api/v1/ai/models` | Список доступных моделей от подключённого LLM-провайдера |
| `POST /api/v1/ai/bundles/generate` | Генерация `bundle.json` по текстовому описанию с автоматической проверкой |

Все вызовы инструментов записываются в журнал аудита `ai_tool_audit` (с возможностью выгрузки в CSV через `GET /api/v1/ai/agent/sessions/{id}/audit?format=csv`).

---

## 4. Пошаговый агент дерева объектов (`/api/v1/ai/agent/**`)

Агент работает по циклу **планирование → вызов инструментов → проверка результата** (до `ispf.ai.agent-max-steps` шагов за один ход, по умолчанию `256`).

- **Многоходовые сессии в БД:** диалоги сохраняются в таблицах `agent_sessions` и `agent_turns` в PostgreSQL (срок хранения по умолчанию 24 часа, `ispf.ai.agent-session-ttl-hours`). Перезапуск сервера или закрытие вкладки браузера не прерывает работу — состояние диалога восстанавливается автоматически.
- **Режим вопроса (`Ask`):** если пользователю нужна только консультация или проверка без изменения объектов, агент работает в режиме чтения без запуска планировщика изменений.

### Методы управления сессиями агента

| Метод и путь | Назначение |
|--------------|------------|
| `GET /api/v1/ai/agent/tools` | Каталог инструментов платформы и их JSON-схемы |
| `POST /api/v1/ai/agent/sessions` | Создать новую сессию чата |
| `GET /api/v1/ai/agent/sessions/{id}` | Загрузить историю сообщений и шагов сессии |
| `GET /api/v1/ai/agent/sessions/{id}/progress` | Живой прогресс выполняющегося хода (`running`, `steps[]`) |
| `POST /api/v1/ai/agent/sessions/{id}/messages` | Отправить задание агенту |
| `POST /api/v1/ai/agent/sessions/{id}/cancel` | Остановить выполняющийся ход |
| `DELETE /api/v1/ai/agent/sessions/{id}` | Удалить сессию |
| `GET /api/v1/ai/agent/sessions/{id}/trace` | Подробная трассировка шагов с задержками и расходом токенов |
| `GET /api/v1/ai/agent/metrics` | Сводная статистика работы агента за выбранное число дней |
| `POST / GET / DELETE /api/v1/ai/agent/sessions/{id}/documents` | Прикрепление текстовых регламентов и ТЗ к сессии чата |

### Основные инструменты агента

| Инструмент | Что делает |
|------------|------------|
| `search_context` | Гибридный поиск по документации, примерам, драйверам и функциям |
| `list_drivers`, `get_driver_help` | Поиск драйвера в каталоге и чтение справки по его параметрам |
| `list_examples`, `get_example_bundle` | Просмотр эталонных пакетов решений (`examples/*`) |
| `list_objects`, `get_object`, `search_objects` | Обзор и поиск узлов в дереве объектов |
| `ensure_singleton_instance`, `instantiate_instance_type` | Создание узлов логики (`SINGLETON` и `INSTANCE`) по шаблонам |
| `create_virtual_device`, `configure_driver`, `driver_control` | Создание устройств, настройка протокольных драйверов и запуск опроса |
| `list_variables`, `describe_variables`, `create_variable`, `set_variable` | Чтение и настройка переменных и правил расчёта (`@bindingRules`) |
| `configure_alert`, `configure_correlator`, `list_automation` | Настройка правил тревог (`ALERT`) и корреляторов событий (`CORRELATOR`) |
| `get_dashboard_layout`, `set_dashboard_layout`, `add_dashboard_widget` | Сборка и редактирование дашбордов (сетка `84×8`) |
| `configure_operator_ui` | Настройка рабочего места оператора (`operatorUi`) |
| `validate_bundle`, `dry_run_deploy`, `import_package` | Проверка и установка пакета `bundle.json` |

---

## 5. Подключение внешних IDE по протоколу MCP (`/api/v1/ai/mcp`)

Включив профиль `mcp`, вы можете подключить к серверу ISPF внешнюю среду разработки (например, **Cursor** или **VS Code**), чтобы внешний ИИ-помощник работал с теми же проверенными инструментами дерева объектов и читал ресурсы `contextpack://info`, `contextpack://bundle-manifest`, `contextpack://widget-types`, `contextpack://driver-catalog`.

Пример настройки `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "ispf": {
      "url": "http://localhost:8080/api/v1/ai/mcp",
      "headers": { "Authorization": "Bearer <токен-администратора>" }
    }
  }
}
```

Подробная инструкция по совместной разработке серверной модели и внешнего React-интерфейса (`ui-pack`) в Cursor приведена в документе **[Разработка собственного веб-приложения во внешней IDE](external-ide.md)**.

---

## 6. Настройка подключения к языковой модели (`application.yml`)

```yaml
ispf:
  ai:
    enabled: true
    provider: openai-compatible   # noop | openai-compatible | ollama | custom-url
    base-url: https://api.openai.com/v1
    model: gpt-4o-mini
    api-key-env: OPENAI_API_KEY
    timeout-seconds: 600
    agent-max-steps: 256
    agent-max-tokens: 16384
    max-tokens: 65536
    temperature: 0.2
    agent-require-approval-for-mutate: true   # на боевых серверах требует подтверждения плана человеком перед изменением объектов
```

- При `agent-require-approval-for-mutate: true` (по умолчанию в промышленном профиле) любые изменяющие действия (`create_object`, `import_package` и др.) приостанавливаются до нажатия кнопки **«Утвердить план»** в интерфейсе, а факт одобрения фиксируется в журнале аудита.
- Ключ API считывается из переменной окружения, указанной в `api-key-env`, и никогда не сохраняется в базе данных или логах.

---

## 7. Работа в веб-консоли: ИИ-студия и быстрый помощник

![ИИ-студия в веб-консоли](../assets/ispf-ai-studio.png)

В консоли администратора доступны два режима работы с ИИ:
1. **Вкладка «ИИ-студия» (`AI Studio`)** — полнофункциональное рабочее место для многошаговых задач: чат с пошаговым агентом дерева объектов, генератор пакетов `bundle.json`, просмотр трассировок и настройка провайдера.
2. **Плавающая кнопка помощника (`Copilot`)** в правом нижнем углу — контекстный консультант по текущему открытому объекту или редактируемой формуле CEL (работает в режиме чтения по текущему фокусу экрана).
